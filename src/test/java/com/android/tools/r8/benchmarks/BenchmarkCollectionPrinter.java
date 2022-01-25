// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import static com.android.tools.r8.utils.ListUtils.map;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.base.Strings;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BenchmarkCollectionPrinter {

  private interface RunnableIO {
    void run() throws IOException;
  }

  private static final PrintStream QUIET =
      new PrintStream(
          new OutputStream() {
            @Override
            public void write(int b) {
              // ignore
            }
          });

  // Internal state for printing the benchmark config for golem.
  private int currentIndent = 0;
  private final PrintStream out;

  public BenchmarkCollectionPrinter(PrintStream out) {
    this.out = out;
  }

  private void scopeBraces(RunnableIO fn) throws IOException {
    print("{");
    indentScope(2, fn);
    print("}");
  }

  private void indentScope(RunnableIO fn) throws IOException {
    indentScope(2, fn);
  }

  private void indentScope(int spaces, RunnableIO fn) throws IOException {
    currentIndent += spaces;
    fn.run();
    currentIndent -= spaces;
  }

  private static String quote(String str) {
    return "\"" + str + "\"";
  }

  private void print(String string) {
    printIndented(string, currentIndent);
  }

  private void printSemi(String string) {
    print(string + ";");
  }

  private void printIndented(String string, int indent) {
    out.print(Strings.repeat(" ", indent));
    out.println(string);
  }

  public void printGolemConfig(Collection<BenchmarkConfig> benchmarks) throws IOException {
    Path jdkHome = getJdkHome();
    Map<String, List<BenchmarkConfig>> nameToTargets = new HashMap<>();
    benchmarks.forEach(
        b -> nameToTargets.computeIfAbsent(b.getName(), k -> new ArrayList<>()).add(b));
    List<String> sortedNames = new ArrayList<>(nameToTargets.keySet());
    sortedNames.sort(String::compareTo);
    print(
        "// AUTOGENERATED FILE from"
            + " src/test/java/com/android/tools/r8/benchmarks/BenchmarkCollection.java");
    print("");
    printSemi("part of r8_config");
    print("");
    print("createTestBenchmarks() {");
    indentScope(
        () -> {
          print("final cpus = [\"Lenovo M90\"];");
          addGolemResource("openjdk", Paths.get(jdkHome + ".tar.gz"));
          for (String benchmarkName : sortedNames) {
            List<BenchmarkConfig> benchmarkTargets = nameToTargets.get(benchmarkName);
            assert !benchmarkTargets.isEmpty();
            scopeBraces(() -> printBenchmarkBlock(benchmarkName, benchmarkTargets));
          }
        });
    print("}");
  }

  private void printBenchmarkBlock(String benchmarkName, List<BenchmarkConfig> benchmarkTargets)
      throws IOException {
    printSemi("final name = " + quote(benchmarkName));
    printSemi("final group = new GroupBenchmark(name + \"Group\", [])");
    // NOTE: It appears these must be consistent for each target now?
    boolean hasWarmup = false;
    String suite = null;
    List<String> metrics = null;
    for (BenchmarkConfig benchmark : benchmarkTargets) {
      if (metrics == null) {
        // TODO: Verify equal on other runs.
        hasWarmup = benchmark.hasTimeWarmupRuns();
        suite = benchmark.getSuite().getDartName();
        metrics = map(benchmark.getMetrics(), BenchmarkMetric::getDartType);
      }
      scopeBraces(
          () -> {
            printSemi("final target = " + quote(benchmark.getTarget().getGolemName()));
            printSemi("final options = group.addTargets(noImplementation, [target])");
            printSemi("options.cpus = cpus");
            printSemi("options.isScript = true");
            printSemi("options.fromRevision = " + benchmark.getFromRevision());
            print("options.mainFile =");
            indentScope(
                4,
                () ->
                    printSemi(
                        quote(
                            "tools/run_benchmark.py --golem"
                                + " --target "
                                + benchmark.getTarget().getIdentifierName()
                                + " --benchmark "
                                + benchmark.getName())));
            printSemi("options.resources.add(openjdk)");
          });
    }

    List<String> finalMetrics = metrics;
    String finalSuite = suite;
    boolean finalHasWarmup = hasWarmup;
    scopeBraces(
        () -> {
          printSemi("final metrics = " + StringUtils.join(", ", finalMetrics, BraceType.SQUARE));
          printSemi("group.addBenchmark(name, metrics)");
          printSemi(finalSuite + ".addBenchmark(name)");
          if (finalHasWarmup) {
            printSemi("final warmupName = name + \"Warmup\"");
            printSemi("group.addBenchmark(warmupName, [Metric.RunTimeRaw])");
            printSemi(finalSuite + ".addBenchmark(warmupName)");
          }
        });
  }

  private void addGolemResource(String name, Path tarball) throws IOException {
    Path shaFile = Paths.get(tarball.toString() + ".sha1");
    downloadDependency(shaFile);
    String sha256 = computeSha256(tarball);
    String shaFileContent = getShaFileContent(shaFile);
    print("final " + name + " = BenchmarkResource(" + quote("") + ",");
    indentScope(
        4,
        () -> {
          print("type: BenchmarkResourceType.Storage,");
          print("uri: " + quote("gs://r8-deps/" + shaFileContent) + ",");
          // Make dart formatter happy.
          if (currentIndent > 6) {
            print("hash:");
            indentScope(4, () -> print(quote(sha256) + ","));
          } else {
            print("hash: " + quote(sha256) + ",");
          }
          print("extract: " + quote("gz") + ");");
        });
  }

  private static Path getJdkHome() throws IOException {
    ProcessBuilder builder = new ProcessBuilder("python", "tools/jdk.py");
    ProcessResult result = ToolHelper.runProcess(builder, QUIET);
    if (result.exitCode != 0) {
      throw new Unreachable("Unexpected failure to determine jdk home: " + result);
    }
    return Paths.get(result.stdout.trim());
  }

  private static String computeSha256(Path path) throws IOException {
    Hasher hasher = Hashing.sha256().newHasher();
    return hasher.putBytes(Files.readAllBytes(path)).hash().toString();
  }

  private static String getShaFileContent(Path path) throws IOException {
    return String.join("\n", Files.readAllLines(path)).trim();
  }

  private static void downloadDependency(Path path) throws IOException {
    ProcessBuilder builder =
        new ProcessBuilder(
            "download_from_google_storage", "-n", "-b", "r8-deps", "-u", "-s", path.toString());
    ProcessResult result = ToolHelper.runProcess(builder, QUIET);
    if (result.exitCode != 0) {
      throw new Unreachable("Unable to download dependency '" + path + "'\n" + result);
    }
  }
}
