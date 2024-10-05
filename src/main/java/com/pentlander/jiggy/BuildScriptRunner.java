package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.DependencyDesc;
import com.pentlander.jiggy.project.ProjectBuild;
import com.pentlander.jiggy.run.ModuleLayerBuilder;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

public class BuildScriptRunner {
  private final String moduleName;
  private final Collection<DependencyDesc> dependencyDescs;
  private final Path buildSourcePath;
  private final Path outputPath;

  private Class<?> buildScript;

  public BuildScriptRunner(String moduleName, Collection<DependencyDesc> dependencyDescs, Path buildSourcePath, Path outputPath) {
    this.moduleName = moduleName;
    this.dependencyDescs = dependencyDescs;
    this.buildSourcePath = buildSourcePath;
    this.outputPath = outputPath;
  }

  void resolveBuildScript() throws IOException {
    var buildScriptDir = buildSourcePath.resolve("jiggy");
    if (Files.exists(buildScriptDir)) {
      var result = new Builder(dependencyDescs).build(buildScriptDir, outputPath.resolve("build-script"));

      var buildScriptPath = buildScriptDir.resolve("java")
          .relativize(buildScriptPath(buildScriptDir));
      var packageName = buildScriptPath.getParent().toString().replace('/', '.');
      var qualifiedClassName = packageName + ".Build";
      var buildModuleName = moduleName + ".jiggy";

      var layer = new ModuleLayerBuilder()
          .qualifiedClassName(qualifiedClassName)
          .moduleName(buildModuleName)
          .modulePath(result.classOutputPath())
          .depModulePaths(result.modulePaths())
          .build();
      try {
        buildScript = layer.findLoader(buildModuleName).loadClass(qualifiedClassName);
        if (Arrays.stream(buildScript.getInterfaces()).noneMatch(clazz -> clazz.equals(ProjectBuild.class))) {
          throw new IllegalStateException("Class '%s' does not implement interface '%s'".formatted(
              buildScript.getName(),
              ProjectBuild.class.getName()));
        }
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private Path buildScriptPath(Path buildScriptDir) throws IOException {
    try (var pathStream = Files.walk(buildScriptDir)) {
      return pathStream.filter(path -> path.getFileName().toString().equals("Build.java"))
          .findFirst()
          .orElseThrow();
    }
  }
}
