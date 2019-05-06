// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileName;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingRetraceTest extends RetraceTestBase {
  private Set<StackTraceLine> haveSeenLines = new HashSet<>();

  @Parameters(name = "Backend: {0}, mode: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(TestParametersBuilder.builder()
            .withDexRuntimesEndingAtExcluding(Version.V5_1_1)
            .withDexRuntimesStartingFromExcluding(Version.V5_1_1).build(),
        CompilationMode.values());
  }

  public VerticalClassMergingRetraceTest(TestParameters parameters, CompilationMode mode) {
    super(parameters.getBackend(), mode);
  }

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(getMainClass(), ResourceWrapper.class, TintResources.class);
  }

  @Override
  public Class<?> getMainClass() {
    return MainApp.class;
  }

  private int expectedActualStackTraceHeight() {
    // In RELEASE mode, a synthetic bridge will be added by vertical class merger.
    return mode == CompilationMode.RELEASE ? 3 : 2;
  }

  private boolean filterSynthesizedMethodWhenLineNumberAvailable(
      StackTraceLine retracedStackTraceLine) {
    return retracedStackTraceLine.lineNumber > 0;
  }

  private boolean filterSynthesizedMethod(StackTraceLine retracedStackTraceLine) {
    return haveSeenLines.add(retracedStackTraceLine)
        && (retracedStackTraceLine.className.contains("ResourceWrapper")
            || retracedStackTraceLine.className.contains("MainApp"));
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          // Even when SourceFile is present retrace replaces the file name in the stack trace.
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethodWhenLineNumberAvailable);
          assertThat(reprocessedStackTrace, isSameExceptForFileName(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethodWhenLineNumberAvailable);
          assertThat(reprocessedStackTrace, isSameExceptForFileName(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    haveSeenLines.clear();
    runTest(
        ImmutableList.of(),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethod);
          assertThat(reprocessedStackTrace,
              isSameExceptForFileNameAndLineNumber(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }
}

class ResourceWrapper {
  // Will be merged down, and represented as:
  //     java.lang.String ...ResourceWrapper.foo() -> a
  @NeverInline
  String foo() {
    throw null;
  }
}

class TintResources extends ResourceWrapper {
}

class MainApp {
  public static void main(String[] args) {
    TintResources t = new TintResources();
    System.out.println(t.foo());
  }
}
