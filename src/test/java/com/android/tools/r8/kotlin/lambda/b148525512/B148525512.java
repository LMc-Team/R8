// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b148525512;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B148525512 extends KotlinTestBase {

  private static final Package pkg = B148525512.class.getPackage();
  private static final String kotlinTestClassesPackage = pkg.getName();
  private static final String baseKtClassName = kotlinTestClassesPackage + ".BaseKt";
  private static final String featureKtClassNamet = kotlinTestClassesPackage + ".FeatureKt";
  private static final String baseClassName = kotlinTestClassesPackage + ".Base";

  private static final KotlinCompileMemoizer kotlinBaseClasses =
      getCompileMemoizer(getKotlinFileInTestPackage(pkg, "base"))
          .configure(
              kotlinCompilerTool -> kotlinCompilerTool.addClasspathFiles(getFeatureApiPath()));
  private static final KotlinCompileMemoizer kotlinFeatureClasses =
      getCompileMemoizer(getKotlinFileInTestPackage(pkg, "feature"))
          .configure(
              kotlinCompilerTool -> {
                // Compile the feature Kotlin code with the base classes on classpath.
                kotlinCompilerTool.addClasspathFiles(
                    kotlinBaseClasses.getForConfiguration(
                        kotlinCompilerTool.getCompiler(), kotlinCompilerTool.getTargetVersion()));
              });
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, target: {1}, kotlinc: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        KotlinTargetVersion.values(),
        getKotlinCompilers());
  }

  public B148525512(
      TestParameters parameters, KotlinTargetVersion targetVersion, KotlinCompiler kotlinc) {
    super(targetVersion, kotlinc);
    this.parameters = parameters;
  }

  private static Path getFeatureApiPath() {
    try {
      Path featureApiJar = getStaticTemp().getRoot().toPath().resolve("feature_api.jar");
      if (Files.exists(featureApiJar)) {
        return featureApiJar;
      }
      writeClassesToJar(featureApiJar, FeatureAPI.class);
      return featureApiJar;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void checkLambdaGroups(CodeInspector inspector) {
    List<FoundClassSubject> lambdaGroups =
        inspector.allClasses().stream()
            .filter(clazz -> clazz.getOriginalName().contains("LambdaGroup"))
            .collect(Collectors.toList());
    assertEquals(1, lambdaGroups.size());
    MethodSubject invokeMethod = lambdaGroups.get(0).uniqueMethodWithName("invoke");
    assertThat(invokeMethod, isPresent());
    // The lambda group has 2 captures which capture "Base".
    assertEquals(
        2,
        invokeMethod
            .streamInstructions()
            .filter(InstructionSubject::isCheckCast)
            .filter(
                instruction ->
                    instruction.asCheckCast().getType().toSourceString().contains("Base"))
            .count());
    // The lambda group has no captures which capture "Feature" (lambdas in the feature are not
    // in this lambda group).
    assertTrue(
        invokeMethod
            .streamInstructions()
            .filter(InstructionSubject::isCheckCast)
            .noneMatch(
                instruction ->
                    instruction.asCheckCast().getType().toSourceString().contains("Feature")));
  }

  @Test
  public void test() throws Exception {
    Path featureCode = temp.newFile("feature.zip").toPath();
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
            .addProgramFiles(kotlinBaseClasses.getForConfiguration(kotlinc, targetVersion))
            .addProgramClasses(FeatureAPI.class)
            .addKeepMainRule(baseKtClassName)
            .addKeepClassAndMembersRules(baseClassName)
            .addKeepClassAndMembersRules(featureKtClassNamet)
            .addKeepClassAndMembersRules(FeatureAPI.class)
            .addOptionsModification(
                options -> options.horizontalClassMergerOptions().disableKotlinLambdaMerging())
            .setMinApi(parameters.getApiLevel())
            .noMinification() // The check cast inspection above relies on original names.
            .addFeatureSplit(
                builder ->
                    builder
                        .addProgramResourceProvider(
                            ArchiveResourceProvider.fromArchive(
                                kotlinFeatureClasses.getForConfiguration(kotlinc, targetVersion),
                                true))
                        .setProgramConsumer(new ArchiveConsumer(featureCode, false))
                        .build())
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .inspect(this::checkLambdaGroups);

    // Run the code without the feature code present.
    compileResult
        .run(parameters.getRuntime(), baseKtClassName)
        .assertSuccessWithOutputLines("1", "2");

    // Run the code with the feature code present.
    compileResult
        .addRunClasspathFiles(featureCode)
        .run(parameters.getRuntime(), baseKtClassName)
        .assertSuccessWithOutputLines("1", "2", "3", "4");
  }
}
