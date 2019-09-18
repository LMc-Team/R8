// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class APIConversionTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withApiLevelsEndingAtExcluding(AndroidApiLevel.M)
        .build();
  }

  public APIConversionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testAPIConversionNoDesugaring() throws Exception {
    testForD8()
        .addInnerClasses(APIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(StringUtils.lines("[5, 6, 7]"));
  }

  @Test
  @Ignore
  public void testAPIConversionDesugaring() throws Exception {
    // TODO(b/): Make library API work when library desugaring is on.
    testForD8()
        .addInnerClasses(APIConversionTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(StringUtils.lines("[5, 6, 7]"));
  }

  static class Executor {

    public static void main(String[] args) {
      int[] ints = new int[3];
      Arrays.setAll(ints, new MyFunction());
      System.out.println(Arrays.toString(ints));
    }
  }

  static class MyFunction implements IntUnaryOperator {

    @Override
    public int applyAsInt(int operand) {
      return operand + 5;
    }
  }
}
