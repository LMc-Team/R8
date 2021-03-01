// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.constraint;

import com.android.tools.r8.graph.ProgramMethod;

public interface ClassInlinerMethodConstraint {

  boolean isEligibleForNewInstanceClassInlining(ProgramMethod method);

  boolean isEligibleForStaticGetClassInlining(ProgramMethod method);

  static AlwaysFalseClassInlinerMethodConstraint alwaysFalse() {
    return AlwaysFalseClassInlinerMethodConstraint.getInstance();
  }

  static AlwaysTrueClassInlinerMethodConstraint alwaysTrue() {
    return AlwaysTrueClassInlinerMethodConstraint.getInstance();
  }

  static OnlyNewInstanceClassInlinerMethodConstraint onlyNewInstanceClassInlining() {
    return OnlyNewInstanceClassInlinerMethodConstraint.getInstance();
  }
}