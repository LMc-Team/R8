// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import static com.android.tools.r8.naming.retraceproguard.StackTrace.isSameExceptForFileName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugarStaticInterfaceMethodsRetraceTest extends RetraceTestBase {

  @Parameters(name = "{0}, mode: {1}, compat: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  public DesugarStaticInterfaceMethodsRetraceTest(
      TestParameters parameters, CompilationMode mode, boolean compat) {
    super(parameters, mode, compat);
  }

  @Override
  public void configure(R8TestBuilder builder) {
    builder.enableInliningAnnotations();
  }

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(
        getMainClass(), InterfaceWithStaticMethod1.class, InterfaceWithStaticMethod2.class);
  }

  @Override
  public Class<?> getMainClass() {
    return MainDesugarStaticInterfaceMethodsRetraceTest.class;
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    // TODO(b/186015503): This test fails when mapping via PCs.
    //  also the test should be updated to use TestParameters and api levels.
    assumeTrue("b/186015503", !parameters.isDexRuntime() || mode != CompilationMode.RELEASE);
    // This also fails when desugaring due to the change in companion method stacks.
    assumeTrue(
        parameters.isCfRuntime()
            || parameters
                .getApiLevel()
                .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport()));
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        // For the desugaring to companion classes the retrace stacktrace is still the same
        // as the mapping file has a fully qualified class name in the method mapping, e.g.:
        //
        //   com.android.tools.r8.naming.retrace.InterfaceWithDefaultMethod1$-CC -> com.android.tools.r8.naming.retrace.a:
        //       1:1:void com.android.tools.r8.naming.retrace.InterfaceWithDefaultMethod1.defaultMethod1():80:80 -> a
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) ->
            assertThat(retracedStackTrace, isSameExceptForFileName(expectedStackTrace)));
  }
}

interface InterfaceWithStaticMethod2 {

  static void staticMethod1() {
    throw null;
  }
}

interface InterfaceWithStaticMethod1 {

  @NeverInline
  static void staticMethod2() {
    InterfaceWithStaticMethod2.staticMethod1();
  }
}

class MainDesugarStaticInterfaceMethodsRetraceTest {

  public static void main(String[] args) {
    InterfaceWithStaticMethod1.staticMethod2();
  }
}
