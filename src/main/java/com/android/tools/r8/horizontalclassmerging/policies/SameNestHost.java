// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;

public class SameNestHost extends MultiClassSameReferencePolicy<DexType> {

  private final DexItemFactory dexItemFactory;

  public SameNestHost(AppView<?> appView) {
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public DexType getMergeKey(DexProgramClass clazz) {
    return clazz.isInANest() ? clazz.getNestHost() : dexItemFactory.objectType;
  }

  @Override
  public String getName() {
    return "SameNestHost";
  }
}
