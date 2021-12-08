// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineHorizontalMergingTest extends TestBase {

  private final AndroidApiLevel libraryClassApiLevel = AndroidApiLevel.K;
  private final AndroidApiLevel otherLibraryClassApiLevel = AndroidApiLevel.K;
  private final AndroidApiLevel firstMethodApiLevel = AndroidApiLevel.M;
  private final AndroidApiLevel secondMethodApiLevel = AndroidApiLevel.O_MR1;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    assumeFalse(
        parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isEqualTo(Version.V12_0_0));
    boolean beforeFirstApiMethodLevel =
        parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(firstMethodApiLevel);
    boolean afterSecondApiMethodLevel =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(secondMethodApiLevel);
    boolean betweenMethodApiLevels = !beforeFirstApiMethodLevel && !afterSecondApiMethodLevel;
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class, OtherLibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, libraryClassApiLevel))
        .apply(
            setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, libraryClassApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getMethod("addedOn23"), firstMethodApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getMethod("addedOn27"), secondMethodApiLevel))
        .apply(setMockApiLevelForClass(OtherLibraryClass.class, otherLibraryClassApiLevel))
        .apply(
            setMockApiLevelForDefaultInstanceInitializer(
                OtherLibraryClass.class, otherLibraryClassApiLevel))
        .apply(
            setMockApiLevelForMethod(
                OtherLibraryClass.class.getMethod("addedOn23"), firstMethodApiLevel))
        .apply(
            setMockApiLevelForMethod(
                OtherLibraryClass.class.getMethod("addedOn27"), secondMethodApiLevel))
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .enableInliningAnnotations()
        .compile()
        .applyIf(
            parameters.isDexRuntime()
                && parameters
                    .getRuntime()
                    .maxSupportedApiLevel()
                    .isGreaterThanOrEqualTo(libraryClassApiLevel),
            b -> b.addBootClasspathClasses(LibraryClass.class, OtherLibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(beforeFirstApiMethodLevel, "Hello World")
        .assertSuccessWithOutputLinesIf(
            betweenMethodApiLevels,
            "LibraryClass::addedOn23",
            "OtherLibraryClass::addedOn23",
            "Hello World")
        .assertSuccessWithOutputLinesIf(
            afterSecondApiMethodLevel,
            "LibraryClass::addedOn23",
            "LibraryClass::addedOn27",
            "OtherLibraryClass::addedOn23",
            "OtherLibraryClass::addedOn27",
            "Hello World")
        .inspect(
            inspector -> {
              // No need to check further on CF.
              List<FoundMethodSubject> outlinedAddedOn23 =
                  inspector.allClasses().stream()
                      .flatMap(clazz -> clazz.allMethods().stream())
                      .filter(
                          methodSubject ->
                              methodSubject.isSynthetic()
                                  && invokesMethodWithName("addedOn23").matches(methodSubject))
                      .collect(Collectors.toList());
              List<FoundMethodSubject> outlinedAddedOn27 =
                  inspector.allClasses().stream()
                      .flatMap(clazz -> clazz.allMethods().stream())
                      .filter(
                          methodSubject ->
                              methodSubject.isSynthetic()
                                  && invokesMethodWithName("addedOn27").matches(methodSubject))
                      .collect(Collectors.toList());
              if (parameters.isCfRuntime()
                  || parameters.getApiLevel().isLessThan(libraryClassApiLevel)) {
                assertTrue(outlinedAddedOn23.isEmpty());
                assertTrue(outlinedAddedOn27.isEmpty());
                assertEquals(3, inspector.allClasses().size());
              } else if (parameters.getApiLevel().isLessThan(firstMethodApiLevel)) {
                // We have generated 4 outlines two having api level 23 and two having api level 27.
                // Check that the levels are horizontally merged.
                assertEquals(5, inspector.allClasses().size());
                assertEquals(2, outlinedAddedOn23.size());
                assertTrue(
                    outlinedAddedOn23.stream()
                        .allMatch(
                            outline ->
                                outline.getMethod().getHolderType()
                                    == outlinedAddedOn23.get(0).getMethod().getHolderType()));
                assertEquals(2, outlinedAddedOn27.size());
                assertTrue(
                    outlinedAddedOn27.stream()
                        .allMatch(
                            outline ->
                                outline.getMethod().getHolderType()
                                    == outlinedAddedOn27.get(0).getMethod().getHolderType()));
              } else if (parameters.getApiLevel().isLessThan(secondMethodApiLevel)) {
                assertTrue(outlinedAddedOn23.isEmpty());
                assertEquals(4, inspector.allClasses().size());
                assertEquals(2, outlinedAddedOn27.size());
                assertTrue(
                    outlinedAddedOn27.stream()
                        .allMatch(
                            outline ->
                                outline.getMethod().getHolderType()
                                    == outlinedAddedOn27.get(0).getMethod().getHolderType()));
              } else {
                // No outlining on this api level.
                assertTrue(outlinedAddedOn23.isEmpty());
                assertTrue(outlinedAddedOn27.isEmpty());
                assertEquals(3, inspector.allClasses().size());
              }
            });
  }

  // Only present from api level 19.
  public static class LibraryClass {

    public void addedOn23() {
      System.out.println("LibraryClass::addedOn23");
    }

    public void addedOn27() {
      System.out.println("LibraryClass::addedOn27");
    }
  }

  // Only present from api level 19.
  public static class OtherLibraryClass {

    public void addedOn23() {
      System.out.println("OtherLibraryClass::addedOn23");
    }

    public static void addedOn27() {
      System.out.println("OtherLibraryClass::addedOn27");
    }
  }

  public static class TestClass {

    @NeverInline
    public static void test() {
      if (AndroidBuildVersion.VERSION >= 19) {
        LibraryClass libraryClass = new LibraryClass();
        if (AndroidBuildVersion.VERSION >= 23) {
          libraryClass.addedOn23();
        }
        if (AndroidBuildVersion.VERSION >= 27) {
          libraryClass.addedOn27();
        }
      }
      if (AndroidBuildVersion.VERSION >= 19) {
        OtherLibraryClass otherLibraryClass = new OtherLibraryClass();
        if (AndroidBuildVersion.VERSION >= 23) {
          otherLibraryClass.addedOn23();
        }
        if (AndroidBuildVersion.VERSION >= 27) {
          OtherLibraryClass.addedOn27();
        }
      }
      System.out.println("Hello World");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      TestClass.test();
    }
  }
}