// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RecordWithMembersTest extends TestBase {

  private static final String RECORD_NAME = "RecordWithMembers";
  private static final byte[][] PROGRAM_DATA = RecordTestUtils.getProgramData(RECORD_NAME);
  private static final String MAIN_TYPE = RecordTestUtils.getMainType(RECORD_NAME);
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "BobX", "43", "BobX", "43", "FelixX", "-1", "FelixX", "-1", "print", "Bob43", "extra");

  private final TestParameters parameters;

  public RecordWithMembersTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    // TODO(b/174431251): This should be replaced with .withCfRuntimes(start = jdk15).
    return buildParameters(
        getTestParameters().withCustomRuntime(CfRuntime.getCheckedInJdk15()).build());
  }

  @Test
  public void testJvm() throws Exception {
    testForJvm()
        .addProgramClassFileData(PROGRAM_DATA)
        .addVmArguments("--enable-preview")
        .run(parameters.getRuntime(), MAIN_TYPE)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
