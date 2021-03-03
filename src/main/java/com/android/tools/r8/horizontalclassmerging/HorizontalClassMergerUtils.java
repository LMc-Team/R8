// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;

public class HorizontalClassMergerUtils {

  public static boolean isClassIdField(AppView<?> appView, DexEncodedField field) {
    DexField classIdField = appView.dexItemFactory().objectMembers.classIdField;
    if (field.isD8R8Synthesized() && field.getType().isIntType()) {
      DexField originalField = appView.graphLens().getOriginalFieldSignature(field.getReference());
      return originalField.match(classIdField);
    }
    assert !appView.graphLens().getOriginalFieldSignature(field.getReference()).match(classIdField);
    return false;
  }
}
