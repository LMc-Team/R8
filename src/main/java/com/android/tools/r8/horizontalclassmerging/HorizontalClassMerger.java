// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.policies.NoFields;
import com.android.tools.r8.horizontalclassmerging.policies.NoInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoStaticClassInitializer;
import com.android.tools.r8.horizontalclassmerging.policies.NotEntryPoint;
import com.android.tools.r8.horizontalclassmerging.policies.SameParentClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ClassMergingEnqueuerExtension;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HorizontalClassMerger {
  private final AppView<AppInfoWithLiveness> appView;
  private final PolicyExecutor policyExecutor;

  public HorizontalClassMerger(
      AppView<AppInfoWithLiveness> appView,
      MainDexTracingResult mainDexClasses,
      ClassMergingEnqueuerExtension classMergingEnqueuerExtension) {
    this.appView = appView;

    List<Policy> policies =
        ImmutableList.of(
            new NoFields(),
            // TODO(b/166071504): Allow merging of classes that implement interfaces.
            new NoInterfaces(),
            new NoStaticClassInitializer(),
            new NoRuntimeTypeChecks(classMergingEnqueuerExtension),
            new NotEntryPoint(appView.dexItemFactory()),
            new SameParentClass()
            // TODO: add policies
            );

    this.policyExecutor = new SimplePolicyExecutor(policies);
  }

  // TODO(b/165577835): replace Collection<DexProgramClass> with MergeGroup
  public HorizontalClassMergerGraphLens run() {
    Map<FieldMultiset, Collection<DexProgramClass>> classes = new HashMap<>();

    // Group classes by same field signature using the hash map.
    for (DexProgramClass clazz : appView.appInfo().app().classesWithDeterministicOrder()) {
      classes.computeIfAbsent(new FieldMultiset(clazz), ignore -> new ArrayList<>()).add(clazz);
    }

    // Run the policies on all collected classes to produce a final grouping.
    Collection<Collection<DexProgramClass>> groups = policyExecutor.run(classes.values());

    HorizontalClassMergerGraphLens.Builder lensBuilder =
        new HorizontalClassMergerGraphLens.Builder();
    FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder =
        new FieldAccessInfoCollectionModifier.Builder();

    // Set up a class merger for each group.
    Collection<ClassMerger> classMergers =
        initializeClassMergers(lensBuilder, fieldAccessChangesBuilder, groups);

    // Merge the classes.
    applyClassMergers(classMergers);

    // Generate the class lens.
    return createLens(lensBuilder, fieldAccessChangesBuilder);
  }

  /**
   * Prepare horizontal class merging by determining which virtual methods and constructors need to
   * be merged and how the merging should be performed.
   */
  private Collection<ClassMerger> initializeClassMergers(
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      Collection<Collection<DexProgramClass>> groups) {
    Collection<ClassMerger> classMergers = new ArrayList<>();

    // TODO(b/166577694): Replace Collection<DexProgramClass> with MergeGroup
    for (Collection<DexProgramClass> group : groups) {
      assert !group.isEmpty();

      DexProgramClass target = group.stream().findFirst().get();
      group.remove(target);

      ClassMerger merger =
          new ClassMerger.Builder(target)
              .addClassesToMerge(group)
              .build(appView, lensBuilder, fieldAccessChangesBuilder);
      classMergers.add(merger);
    }

    return classMergers;
  }

  /** Merges all class groups using {@link ClassMerger}. */
  private void applyClassMergers(Collection<ClassMerger> classMergers) {
    for (ClassMerger merger : classMergers) {
      merger.mergeGroup();
    }
  }

  /**
   * Fix all references to merged classes using the {@link TreeFixer}. Construct a graph lens
   * containing all changes performed by horizontal class merging.
   */
  private HorizontalClassMergerGraphLens createLens(
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder) {

    HorizontalClassMergerGraphLens lens =
        new TreeFixer(appView, lensBuilder, fieldAccessChangesBuilder).fixupTypeReferences();
    return lens;
  }
}
