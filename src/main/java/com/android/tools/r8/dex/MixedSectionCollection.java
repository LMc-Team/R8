// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationDirectory;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexDebugInfoForWriting;
import com.android.tools.r8.graph.DexEncodedArray;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexWritableCode;
import com.android.tools.r8.graph.ParameterAnnotationsList;

/**
 * Collection of the various components of the mixed section of a dex file.
 *
 * <p>This semantically is just a wrapper around a bunch of collections. We do not expose the
 * collections directly to allow for implementations that under the hood do not use collections.
 *
 * <p>See {@link DexItem#collectMixedSectionItems(MixedSectionCollection)} for
 * information on how to fill a {@link MixedSectionCollection}.
 */
public abstract class MixedSectionCollection {

  /**
   * Adds the given class data to the collection.
   *
   * Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(DexProgramClass dexClassData);

  /**
   * Adds the given encoded array to the collection.
   *
   * Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(DexEncodedArray dexEncodedArray);

  /**
   * Adds the given annotation set to the collection.
   *
   * Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(DexAnnotationSet dexAnnotationSet);

  /**
   * Recurse on the given encoded method to add items to the collection.
   *
   * <p>Allows overriding the behavior when dex-file writing.
   */
  public void visit(DexEncodedMethod dexEncodedMethod) {
    dexEncodedMethod.collectMixedSectionItemsWithCodeMapping(this);
  }

  /**
   * Adds the given code item to the collection.
   *
   * <p>Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(DexEncodedMethod method, DexWritableCode dexCode);

  /**
   * Adds the given debug info to the collection.
   *
   * <p>Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(DexDebugInfoForWriting dexDebugInfo);

  /**
   * Adds the given type list to the collection.
   *
   * Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(DexTypeList dexTypeList);

  /**
   * Adds the given annotation-set reference list to the collection.
   *
   * Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(ParameterAnnotationsList annotationSetRefList);

  /**
   * Adds the given annotation to the collection.
   *
   * Does not add any dependencies.
   *
   * @return true if the item was not added before
   */
  public abstract boolean add(DexAnnotation annotation);

  /**
   * Adds the given annotation directory to the collection.
   *
   * <p>Adds a dependency between the clazz and the annotation directory.
   */
  public abstract void setAnnotationsDirectoryForClass(
      DexProgramClass clazz, DexAnnotationDirectory annotationDirectory);

  /**
   * Adds the given static field values array to the collection.
   *
   * <p>Adds a dependency between the clazz and the static field values array.
   */
  public abstract void setStaticFieldValuesForClass(
      DexProgramClass clazz, DexEncodedArray staticFieldValues);
}
