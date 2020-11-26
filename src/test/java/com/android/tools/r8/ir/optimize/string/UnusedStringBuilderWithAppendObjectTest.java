// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnusedStringBuilderWithAppendObjectTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedStringBuilderWithAppendObjectTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector ->
                // TODO(b/174285670): StringBuilder should be removed.
                assertThat(
                    inspector.clazz(Main.class).mainMethod(), invokesMethodWithName("append")))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      A a = System.currentTimeMillis() > 0 ? new A() : null;
      new StringBuilder().append(a).toString();
    }
  }

  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }
}
