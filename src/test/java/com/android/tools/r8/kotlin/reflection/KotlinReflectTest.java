// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.reflection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinReflectTest extends KotlinTestBase {

  private final TestParameters parameters;
  private static final String EXPECTED_OUTPUT = "Hello World!";
  private static final String PKG = KotlinReflectTest.class.getPackage().getName();
  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "SimpleReflect" + FileUtils.KT_EXTENSION));

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public KotlinReflectTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testCf() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramFiles(compiledJars.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .addProgramFiles(ToolHelper.getKotlinReflectJar(kotlinc))
        .run(parameters.getRuntime(), PKG + ".SimpleReflectKt")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    final File output = temp.newFile("output.zip");
    testForD8(parameters.getBackend())
        .addProgramFiles(compiledJars.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .addProgramFiles(ToolHelper.getKotlinReflectJar(kotlinc))
        .setProgramConsumer(new ArchiveConsumer(output.toPath(), true))
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(
            options -> {
              options.testing.enableD8ResourcesPassThrough = true;
              options.dataResourceConsumer = options.programConsumer.getDataResourceConsumer();
            })
        .run(parameters.getRuntime(), PKG + ".SimpleReflectKt")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    final File foo = temp.newFile("foo");
    testForR8(parameters.getBackend())
        .addProgramFiles(compiledJars.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
        .addProgramFiles(ToolHelper.getKotlinReflectJar(kotlinc))
        .addProgramFiles(ToolHelper.getKotlinAnnotationJar(kotlinc))
        .setMinApi(parameters.getApiLevel())
        .addKeepAllClassesRule()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .allowDiagnosticWarningMessages()
        .compile()
        .writeToZip(foo.toPath())
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), PKG + ".SimpleReflectKt")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }
}
