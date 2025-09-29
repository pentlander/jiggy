package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.MainConfig;
import com.pentlander.jiggy.dep.ModuleDep;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

public class ApplicationPackager {
  private static final String MODULES = "java.compiler,java.instrument,java.logging,java"
      + ".management,java.naming,java.net.http,java.security.sasl,java.sql,java.sql.rowset,java"
      + ".transaction.xa,java.xml,jdk.localedata";
  private final Path jarPath;
  private final Path appOutputPath;

  public ApplicationPackager(Path jarPath, Path outputPath) {
    this.jarPath = jarPath;
    this.appOutputPath = outputPath.resolve("application");
  }

  public record PackageResult(List<Path> depDirectories) {
    public String depModulePath() {
      return depDirectories.stream().map(Path::toString).collect(Collectors.joining(
          ":"));
    }
  }

  public PackageResult packageDeps(Collection<ModuleDep> deps) {
    var depDirs = new ArrayList<Path>();
    var libPath = appOutputPath.resolve("lib");
    if (Files.notExists(libPath)) {
      try {
        Files.createDirectories(libPath);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    depDirs.add(libPath);

    deps.parallelStream().forEach(directDep -> {
      try {
        var filePath = directDep.jarFile().toPath();
        Files.copy(
            filePath,
            libPath.resolve(filePath.getFileName()),
            StandardCopyOption.REPLACE_EXISTING);
        for (var dep : directDep.deps()) {
          var depFilePath = dep.jarFile().toPath();
          Files.copy(
              depFilePath,
              libPath.resolve(depFilePath.getFileName()),
              StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    try {
      Files.copy(
          jarPath,
          libPath.resolve(jarPath.getFileName()),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return new PackageResult(depDirs);
  }

  String jarModules(Path jarPath, PackageResult packageResult) {
    var jdeps = ToolProvider.findFirst("jdeps").orElseThrow();
    var modulePath =  packageResult.depModulePath();
    var outStream = new ByteArrayOutputStream();
    var code = jdeps.run(new PrintStream(outStream), System.err,
        "--recursive",
        "--ignore-missing-deps",
        "-quiet",
        "--print-module-deps",
        "--regex",
        "(java.*)|(jdk.*)",
        "--module-path",
        modulePath,
        jarPath.toString());

    var result = outStream.toString().strip();
    if (code != 0) {
      throw new RuntimeException("Failed to run jdeps: " + result);
    }

    return result;
  }

  public Path createImage() {
    var imageOutputPath = appOutputPath.resolve("image");
    if (!Files.exists(imageOutputPath)) {
      var jlink = ToolProvider.findFirst("jlink").orElseThrow();
      int result = jlink.run(System.out, System.err,
          "--strip-debug",
          "--compress",
          "zip-6",
          "--no-header-files",
          "--no-man-pages",
          "--add-modules",
          MODULES,
          "--output",
          imageOutputPath.toString());
      if (result != 0) {
        throw new RuntimeException("Failed to package application.");
      }
    }

    return imageOutputPath.resolve("bin", "java");
  }

  public void createLaunchScript(String scriptName, MainConfig mainConfig) throws IOException {
    var launchScriptPath = appOutputPath.resolve(scriptName);
    Files.writeString(launchScriptPath, generateLaunchScript(mainConfig));
    var wasSetExecutable = launchScriptPath.toFile().setExecutable(true);
    if (!wasSetExecutable) {
      throw new IOException("Failed to make launch script executable: " + launchScriptPath);
    }
  }

  private String generateLaunchScript(MainConfig mainConfig) {
    var defaultJvmOptsArray = mainConfig.defaultJvmOpts().stream()
        .map(opt -> "\"" + opt.replace("\"", "\\\"") + "\"")
        .collect(Collectors.joining(" "));

    return """
        #!/bin/bash

        # Find script directory and set paths
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        JAVA_CMD="$SCRIPT_DIR/image/bin/java"
        MODULE_PATH="$SCRIPT_DIR/lib"

        DEFAULT_JVM_OPTS=(%s)
        MAIN_CLASS="%s"

        # Run the application
        exec "$JAVA_CMD" "${DEFAULT_JVM_OPTS[@]}" $JAVA_OPTS --module-path "$MODULE_PATH" --module "$MAIN_CLASS" "$@"
        """.formatted(defaultJvmOptsArray,
        mainConfig.moduleName() + "/" + mainConfig.className());
  }

}
