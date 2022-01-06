// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DebugBytecodeWriter;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.LebUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class DexDebugInfo extends CachedHashValueDexItem
    implements StructuralItem<DexDebugInfo> {

  private enum DebugInfoKind {
    EVENT_BASED,
    PC_BASED
  }

  abstract DebugInfoKind getKind();

  abstract int internalAcceptCompareTo(DexDebugInfo other, CompareToVisitor visitor);

  abstract int getParameterCount();

  public boolean isEventBasedInfo() {
    return getKind() == DebugInfoKind.EVENT_BASED;
  }

  public boolean isPcBasedInfo() {
    return getKind() == DebugInfoKind.PC_BASED;
  }

  public EventBasedDebugInfo asEventBasedInfo() {
    return null;
  }

  public PcBasedDebugInfo asPcBasedInfo() {
    return null;
  }

  @Override
  abstract void collectMixedSectionItems(MixedSectionCollection collection);

  @Override
  public abstract DexDebugInfo self();

  @Override
  public StructuralMapping<DexDebugInfo> getStructuralMapping() {
    throw new Unreachable();
  }

  @Override
  public abstract void acceptHashing(HashingVisitor visitor);

  @Override
  public int acceptCompareTo(DexDebugInfo other, CompareToVisitor visitor) {
    int diff = visitor.visitInt(getKind().ordinal(), other.getKind().ordinal());
    if (diff != 0) {
      return diff;
    }
    return internalAcceptCompareTo(other, visitor);
  }

  @Override
  protected final boolean computeEquals(Object other) {
    return Equatable.equalsImpl(this, other);
  }

  public static class PcBasedDebugInfo extends DexDebugInfo implements DexDebugInfoForWriting {
    private static final int START_LINE = 0;
    private final int parameterCount;
    private final int maxPc;

    private static void specify(StructuralSpecification<PcBasedDebugInfo, ?> spec) {
      spec.withInt(d -> d.parameterCount).withInt(d -> d.maxPc);
    }

    public PcBasedDebugInfo(int parameterCount, int maxPc) {
      this.parameterCount = parameterCount;
      this.maxPc = maxPc;
    }

    @Override
    public int getParameterCount() {
      return parameterCount;
    }

    @Override
    public DexDebugInfo self() {
      return this;
    }

    @Override
    public PcBasedDebugInfo asPcBasedInfo() {
      return this;
    }

    @Override
    DebugInfoKind getKind() {
      return DebugInfoKind.PC_BASED;
    }

    @Override
    protected int computeHashCode() {
      return Objects.hash(parameterCount, maxPc);
    }

    @Override
    public void acceptHashing(HashingVisitor visitor) {
      visitor.visit(this, PcBasedDebugInfo::specify);
    }

    @Override
    int internalAcceptCompareTo(DexDebugInfo other, CompareToVisitor visitor) {
      assert other.isPcBasedInfo();
      return visitor.visit(this, other.asPcBasedInfo(), PcBasedDebugInfo::specify);
    }

    @Override
    public void collectMixedSectionItems(MixedSectionCollection collection) {
      collection.add(this);
    }

    @Override
    public void collectIndexedItems(IndexedItemCollection indexedItems, GraphLens graphLens) {
      // No indexed items to collect.
    }

    @Override
    public int estimatedWriteSize() {
      return LebUtils.sizeAsUleb128(START_LINE)
          + LebUtils.sizeAsUleb128(parameterCount)
          + parameterCount * LebUtils.sizeAsUleb128(0)
          + 1
          + maxPc
          + 1;
    }

    @Override
    public void write(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putUleb128(START_LINE);
      writer.putUleb128(parameterCount);
      for (int i = 0; i < parameterCount; i++) {
        writer.putString(null);
      }
      DexDebugEvent.ZERO_CHANGE_DEFAULT_EVENT.writeOn(writer, mapping, graphLens);
      for (int i = 0; i < maxPc; i++) {
        throw new Unimplemented("add 1,1 increment");
      }
      writer.putByte(Constants.DBG_END_SEQUENCE);
    }

    @Override
    public String toString() {
      return "PcBasedDebugInfo (params: " + parameterCount + ", max-pc: " + maxPc + ")";
    }
  }

  public static class EventBasedDebugInfo extends DexDebugInfo {

    public final int startLine;
    public final DexString[] parameters;
    public DexDebugEvent[] events;

    private static void specify(StructuralSpecification<EventBasedDebugInfo, ?> spec) {
      spec.withInt(d -> d.startLine)
          .withItemArrayAllowingNullMembers(d -> d.parameters)
          .withItemArray(d -> d.events);
    }

    public EventBasedDebugInfo(int startLine, DexString[] parameters, DexDebugEvent[] events) {
      assert startLine >= 0;
      this.startLine = startLine;
      this.parameters = parameters;
      this.events = events;
    }

    @Override
    public DexDebugInfo self() {
      return this;
    }

    @Override
    public EventBasedDebugInfo asEventBasedInfo() {
      return this;
    }

    @Override
    DebugInfoKind getKind() {
      return DebugInfoKind.EVENT_BASED;
    }

    @Override
    int getParameterCount() {
      return parameters.length;
    }

    public List<DexDebugEntry> computeEntries(DexMethod method) {
      DexDebugEntryBuilder builder = new DexDebugEntryBuilder(startLine, method);
      for (DexDebugEvent event : events) {
        event.accept(builder);
      }
      return builder.build();
    }

    @Override
    public int computeHashCode() {
      return startLine + Arrays.hashCode(parameters) * 7 + Arrays.hashCode(events) * 13;
    }

    @Override
    public void acceptHashing(HashingVisitor visitor) {
      visitor.visit(this, EventBasedDebugInfo::specify);
    }

    @Override
    int internalAcceptCompareTo(DexDebugInfo other, CompareToVisitor visitor) {
      assert other.isEventBasedInfo();
      return visitor.visit(this, other.asEventBasedInfo(), EventBasedDebugInfo::specify);
    }

    public void collectIndexedItems(IndexedItemCollection indexedItems, GraphLens graphLens) {
      for (DexString parameter : parameters) {
        if (parameter != null) {
          parameter.collectIndexedItems(indexedItems);
        }
      }
      for (DexDebugEvent event : events) {
        event.collectIndexedItems(indexedItems, graphLens);
      }
    }

    @Override
    void collectMixedSectionItems(MixedSectionCollection collection) {
      // Only writable info should be iterated for collection.
      throw new Unreachable();
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("DebugInfo (line " + startLine + ") events: [\n");
      for (DexDebugEvent event : events) {
        builder.append("  ").append(event).append("\n");
      }
      builder.append("  END_SEQUENCE\n");
      builder.append("]\n");
      return builder.toString();
    }
  }

  public static DexDebugInfoForWriting convertToWritable(DexDebugInfo debugInfo) {
    if (debugInfo == null) {
      return null;
    }
    if (debugInfo.isPcBasedInfo()) {
      return debugInfo.asPcBasedInfo();
    }
    EventBasedDebugInfo eventBasedInfo = debugInfo.asEventBasedInfo();
    DexDebugEvent[] writableEvents =
        ArrayUtils.filter(
            eventBasedInfo.events, DexDebugEvent::isWritableEvent, DexDebugEvent.EMPTY_ARRAY);
    return new WritableEventBasedDebugInfo(
        eventBasedInfo.startLine, eventBasedInfo.parameters, writableEvents);
  }

  private static class WritableEventBasedDebugInfo extends EventBasedDebugInfo
      implements DexDebugInfoForWriting {

    private WritableEventBasedDebugInfo(
        int startLine, DexString[] parameters, DexDebugEvent[] writableEvents) {
      super(startLine, parameters, writableEvents);
    }

    @Override
    public void collectIndexedItems(IndexedItemCollection indexedItems, GraphLens graphLens) {
      super.collectIndexedItems(indexedItems, graphLens);
    }

    @Override
    public void collectMixedSectionItems(MixedSectionCollection collection) {
      collection.add(this);
    }

    @Override
    public int estimatedWriteSize() {
      return LebUtils.sizeAsUleb128(startLine)
          + LebUtils.sizeAsUleb128(parameters.length)
          // Estimate 4 bytes per parameter pointer.
          + parameters.length * 4
          + events.length
          + 1;
    }

    @Override
    public void write(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putUleb128(startLine);
      writer.putUleb128(parameters.length);
      for (DexString name : parameters) {
        writer.putString(name);
      }
      for (DexDebugEvent event : events) {
        event.writeOn(writer, mapping, graphLens);
      }
      writer.putByte(Constants.DBG_END_SEQUENCE);
    }
  }
}
