// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class L8TestBuilder {

  private final AndroidApiLevel apiLevel;
  private final Backend backend;
  private final TestState state;

  private CompilationMode mode = CompilationMode.RELEASE;
  private String generatedKeepRules = null;
  private List<String> keepRules = new ArrayList<>();
  private List<Path> programFiles = new ArrayList<>();
  private List<byte[]> programClassFileData = new ArrayList<>();
  private Consumer<InternalOptions> optionsModifier = ConsumerUtils.emptyConsumer();
  private StringResource desugaredLibrarySpecification = null;
  private List<Path> libraryFiles = new ArrayList<>();
  private ProgramConsumer programConsumer;
  private boolean finalPrefixVerification = true;

  private L8TestBuilder(AndroidApiLevel apiLevel, Backend backend, TestState state) {
    this.apiLevel = apiLevel;
    this.backend = backend;
    this.state = state;
  }

  public static L8TestBuilder create(AndroidApiLevel apiLevel, Backend backend, TestState state) {
    return new L8TestBuilder(apiLevel, backend, state);
  }

  public L8TestBuilder ignoreFinalPrefixVerification() {
    finalPrefixVerification = false;
    return this;
  }

  public L8TestBuilder addProgramFiles(Path... programFiles) {
    this.programFiles.addAll(Arrays.asList(programFiles));
    return this;
  }

  public L8TestBuilder addProgramFiles(Collection<Path> programFiles) {
    this.programFiles.addAll(programFiles);
    return this;
  }

  public L8TestBuilder addProgramClassFileData(byte[]... classes) {
    this.programClassFileData.addAll(Arrays.asList(classes));
    return this;
  }

  public L8TestBuilder addLibraryFiles(Collection<Path> libraryFiles) {
    this.libraryFiles.addAll(libraryFiles);
    return this;
  }

  public L8TestBuilder addLibraryFiles(Path... libraryFiles) {
    Collections.addAll(this.libraryFiles, libraryFiles);
    return this;
  }

  public L8TestBuilder addGeneratedKeepRules(String generatedKeepRules) {
    assertNull(this.generatedKeepRules);
    this.generatedKeepRules = generatedKeepRules;
    return this;
  }

  public L8TestBuilder addKeepRules(String keepRule) throws IOException {
    this.keepRules.add(keepRule);
    return this;
  }

  public L8TestBuilder addKeepRuleFile(Path keepRuleFile) throws IOException {
    this.keepRules.add(FileUtils.readTextFile(keepRuleFile, StandardCharsets.UTF_8));
    return this;
  }

  public L8TestBuilder addKeepRuleFiles(Collection<Path> keepRuleFiles) throws IOException {
    for (Path keepRuleFile : keepRuleFiles) {
      addKeepRuleFile(keepRuleFile);
    }
    return this;
  }

  public L8TestBuilder addOptionsModifier(Consumer<InternalOptions> optionsModifier) {
    this.optionsModifier = this.optionsModifier.andThen(optionsModifier);
    return this;
  }

  public L8TestBuilder apply(ThrowableConsumer<L8TestBuilder> thenConsumer) {
    thenConsumer.acceptWithRuntimeException(this);
    return this;
  }

  public L8TestBuilder applyIf(boolean condition, ThrowableConsumer<L8TestBuilder> thenConsumer) {
    return applyIf(condition, thenConsumer, ThrowableConsumer.empty());
  }

  public L8TestBuilder applyIf(
      boolean condition,
      ThrowableConsumer<L8TestBuilder> thenConsumer,
      ThrowableConsumer<L8TestBuilder> elseConsumer) {
    if (condition) {
      thenConsumer.acceptWithRuntimeException(this);
    } else {
      elseConsumer.acceptWithRuntimeException(this);
    }
    return this;
  }

  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  public L8TestBuilder setDebug() {
    this.mode = CompilationMode.DEBUG;
    return this;
  }

  public L8TestBuilder setProgramConsumer(ProgramConsumer programConsumer) {
    this.programConsumer = programConsumer;
    return this;
  }

  public L8TestBuilder setDesugaredLibrarySpecification(Path path) {
    this.desugaredLibrarySpecification = StringResource.fromFile(path);
    return this;
  }

  private ProgramConsumer computeProgramConsumer(AndroidAppConsumers sink) {
    if (programConsumer != null) {
      return programConsumer;
    }
    return backend.isCf()
        ? sink.wrapProgramConsumer(ClassFileConsumer.emptyConsumer())
        : sink.wrapProgramConsumer(DexIndexedConsumer.emptyConsumer());
  }

  public L8TestCompileResult compile()
      throws IOException, CompilationFailedException, ExecutionException {
    // We wrap exceptions in a RuntimeException to call this from a lambda.
    AndroidAppConsumers sink = new AndroidAppConsumers();
    L8Command.Builder l8Builder =
        L8Command.builder(state.getDiagnosticsHandler())
            .addProgramFiles(programFiles)
            .addLibraryFiles(getLibraryFiles())
            .setMode(mode)
            .setIncludeClassesChecksum(true)
            .addDesugaredLibraryConfiguration(desugaredLibrarySpecification)
            .setMinApiLevel(apiLevel.getLevel())
            .setProgramConsumer(computeProgramConsumer(sink));
    addProgramClassFileData(l8Builder);
    Path mapping = null;
    ImmutableList<String> allKeepRules = null;
    if (!keepRules.isEmpty() || generatedKeepRules != null) {
      mapping = state.getNewTempFile("mapping.txt");
      allKeepRules =
          ImmutableList.<String>builder()
              .addAll(keepRules)
              .addAll(
                  generatedKeepRules != null
                      ? ImmutableList.of(generatedKeepRules)
                      : Collections.emptyList())
              .build();
      l8Builder
          .addProguardConfiguration(allKeepRules, Origin.unknown())
          .setProguardMapOutputPath(mapping);
    }
    ToolHelper.runL8(l8Builder.build(), optionsModifier);
    // With special program consumer we may not be able to build the resulting app.
    if (programConsumer != null) {
      return null;
    }
    return new L8TestCompileResult(
            sink.build(),
            apiLevel,
            allKeepRules,
            generatedKeepRules,
            mapping,
            state,
            backend.isCf() ? OutputMode.ClassFile : OutputMode.DexIndexed)
        .applyIf(
            finalPrefixVerification,
            compileResult ->
                compileResult.inspect(
                    inspector ->
                        inspector.forAllClasses(
                            clazz ->
                                assertTrue(
                                    clazz.getFinalName().startsWith("j$.")
                                        || clazz.getFinalName().startsWith("java.")))));
  }

  private L8Command.Builder addProgramClassFileData(L8Command.Builder builder) {
    programClassFileData.forEach(data -> builder.addClassProgramData(data, Origin.unknown()));
    return builder;
  }

  private Collection<Path> getLibraryFiles() {
    return libraryFiles;
  }
}
