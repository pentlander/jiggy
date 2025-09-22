package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.MainConfig;
import com.pentlander.jiggy.dep.ModuleDep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

public class ApplicationPackager {
  private final Path outputPath;

  public ApplicationPackager(Path outputPath) {
    this.outputPath = outputPath;
  }

  public record PackageResult(List<Path> depDirectories) {
    public String depModulePath() {
      return depDirectories.stream().map(Path::toString).collect(Collectors.joining(
          ":"));
    }
  }

  public PackageResult packageDeps(Collection<ModuleDep> deps) {
    var appOutputPath = outputPath.resolve("application");
    var depDirs = new ArrayList<Path>();
    deps.parallelStream().forEach(directDep -> {
      var moduleName = Objects.requireNonNull(directDep.moduleName().name());
      var depDir = appOutputPath.resolve(moduleName);
      try {
        Files.createDirectories(depDir);
        var filePath = directDep.jarFile().toPath();
        Files.copy(
            filePath,
            depDir.resolve(filePath.getFileName()),
            StandardCopyOption.REPLACE_EXISTING);
        for (var dep : directDep.deps()) {
          var depFilePath = dep.jarFile().toPath();
          Files.copy(
              depFilePath,
              depDir.resolve(depFilePath.getFileName()),
              StandardCopyOption.REPLACE_EXISTING);
        }
        depDirs.add(depDir);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    return new PackageResult(depDirs);
  }

  public void link(
      MainConfig mainConfig,
      Path jarPath,
      PackageResult packageResult
  ) {
    var jlink = ToolProvider.findFirst("jlink").orElseThrow();
    var modulePath =  jarPath + ":" + packageResult.depModulePath();

    System.out.println("Module path: " + modulePath);
    var command = "%s=%s".formatted(mainConfig.moduleName(), mainConfig.moduleName());
    int result = jlink.run(
        System.out,
        System.err,
        "--module-path",
        modulePath,
        "--add-modules",
        mainConfig.moduleName(),
        "--launcher",
        command,
        "--output",
        outputPath.resolve("launcher").toString());
    if (result != 0) {
      throw new RuntimeException("Failed to package application.");
    }
  }
}
