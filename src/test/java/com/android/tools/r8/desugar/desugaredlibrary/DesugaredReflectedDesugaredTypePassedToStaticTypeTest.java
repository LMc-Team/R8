// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.time.LocalDate;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredReflectedDesugaredTypePassedToStaticTypeTest
    extends DesugaredLibraryTestBase {

  private static final String EXPECTED = StringUtils.lines("1992", "1992");
  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public DesugaredReflectedDesugaredTypePassedToStaticTypeTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testDate() throws Exception {
    SingleTestRunResult<?> runResult =
        testForDesugaredLibrary(
                parameters, libraryDesugaringSpecification, compilationSpecification)
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .run(parameters.getRuntime(), Main.class);
    if (!libraryDesugaringSpecification.hasTimeDesugaring(parameters)) {
      runResult.assertFailureWithErrorThatMatches(
          containsString("java.lang.ClassNotFoundException: j$.time.LocalDate"));
    } else {
      runResult.assertSuccessWithOutput(EXPECTED);
    }
  }

  public static class Main {

    public static void printYear(LocalDate date) {
      System.out.println(date.getYear());
    }

    public static void main(String[] args) throws Exception {
      printYear(LocalDate.of(1992, 1, 1));
      printYear(
          (LocalDate)
              Class.forName("j$.time.LocalDate")
                  .getMethod("of", int.class, int.class, int.class)
                  .invoke(null, 1992, 1, 1));
    }
  }
}
