// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A graph lens that will not lead to any code rewritings in the {@link
 * com.android.tools.r8.ir.conversion.LensCodeRewriter}, or parameter removals in the {@link
 * com.android.tools.r8.ir.conversion.IRBuilder}.
 *
 * <p>The mappings from the original program to the generated program are kept, though.
 */
public final class AppliedGraphLens extends NonIdentityGraphLens {

  private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> renamedTypeNames =
      BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
  private final BiMap<DexField, DexField> originalFieldSignatures = HashBiMap.create();
  private final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

  // Original method signatures for bridges and companion methods. Due to the synthesis of these
  // methods, the mapping from original methods to final methods is not one-to-one, but one-to-many,
  // which is why we need an additional map.
  private final Map<DexMethod, DexMethod> extraOriginalMethodSignatures = new IdentityHashMap<>();

  public AppliedGraphLens(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView.dexItemFactory(), GraphLens.getIdentityLens());
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      // TODO(b/169395592): If merged classes were removed from the application this would not be
      //  necessary.
      if (appView.graphLens().lookupType(clazz.getType()) != clazz.getType()) {
        continue;
      }

      // Record original type names.
      recordOriginalTypeNames(clazz, appView);

      // Record original field signatures.
      for (DexEncodedField encodedField : clazz.fields()) {
        DexField field = encodedField.getReference();
        DexField original = appView.graphLens().getOriginalFieldSignature(field);
        if (original != field) {
          DexField existing = originalFieldSignatures.forcePut(field, original);
          assert existing == null;
        }
      }

      // Record original method signatures.
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        DexMethod method = encodedMethod.getReference();
        DexMethod original = appView.graphLens().getOriginalMethodSignature(method);
        DexMethod existing = originalMethodSignatures.inverse().get(original);
        if (existing == null) {
          originalMethodSignatures.put(method, original);
        } else {
          DexMethod renamed = appView.graphLens().getRenamedMethodSignature(original);
          if (renamed == existing) {
            extraOriginalMethodSignatures.put(method, original);
          } else {
            originalMethodSignatures.forcePut(method, original);
            extraOriginalMethodSignatures.put(existing, original);
          }
        }
      }
    }

    // Trim original method signatures.
    MapUtils.removeIdentityMappings(originalMethodSignatures);
    MapUtils.removeIdentityMappings(extraOriginalMethodSignatures);
  }

  private void recordOriginalTypeNames(
      DexProgramClass clazz, AppView<? extends AppInfoWithClassHierarchy> appView) {
    DexType type = clazz.getType();

    List<DexType> originalTypes = Lists.newArrayList(appView.graphLens().getOriginalTypes(type));
    boolean isIdentity = originalTypes.size() == 1 && originalTypes.get(0) == type;
    if (!isIdentity) {
      originalTypes.forEach(
          originalType -> {
            assert !renamedTypeNames.containsKey(originalType);
            renamedTypeNames.put(originalType, type);
          });
      renamedTypeNames.setRepresentative(type, appView.graphLens().getOriginalType(type));
    }
  }

  @Override
  public boolean isAppliedLens() {
    return true;
  }

  @Override
  public DexType getOriginalType(DexType type) {
    return renamedTypeNames.getRepresentativeKeyOrDefault(type, type);
  }

  @Override
  public Iterable<DexType> getOriginalTypes(DexType type) {
    Set<DexType> originalTypes = renamedTypeNames.getKeys(type);
    return originalTypes.isEmpty() ? ImmutableList.of(type) : originalTypes;
  }

  @Override
  public DexField getOriginalFieldSignature(DexField field) {
    return originalFieldSignatures.getOrDefault(field, field);
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField, GraphLens codeLens) {
    if (this == codeLens) {
      return originalField;
    }
    return originalFieldSignatures.inverse().getOrDefault(originalField, originalField);
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    return this != applied
        ? originalMethodSignatures.inverse().getOrDefault(originalMethod, originalMethod)
        : originalMethod;
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens) {
    return GraphLens.getIdentityLens().lookupPrototypeChangesForMethodDefinition(method, codeLens);
  }

  @Override
  public DexType internalDescribeLookupClassType(DexType previous) {
    return renamedTypeNames.getOrDefault(previous, previous);
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    return previous;
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    return previous;
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    if (extraOriginalMethodSignatures.containsKey(method)) {
      return extraOriginalMethodSignatures.get(method);
    }
    return originalMethodSignatures.getOrDefault(method, method);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return true;
  }

  @Override
  public boolean hasCodeRewritings() {
    return false;
  }
}
