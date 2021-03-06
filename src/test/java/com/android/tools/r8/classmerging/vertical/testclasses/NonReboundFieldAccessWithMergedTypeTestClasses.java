// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical.testclasses;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.classmerging.vertical.NonReboundFieldAccessWithMergedTypeTest.GreetingBase;

public class NonReboundFieldAccessWithMergedTypeTestClasses {

  @NoVerticalClassMerging
  static class A {

    public GreetingBase greeting;

    A(GreetingBase greeting) {
      this.greeting = greeting;
    }
  }

  @NoVerticalClassMerging
  public static class B extends A {

    public B(GreetingBase greeting) {
      super(greeting);
    }
  }
}
