package com.pentlander.jiggy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.pentlander.jiggy.BuildConfig.MainConfig;
import com.pentlander.jiggy.LayeredRunner.ModulePath;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;

public class Main {
  public static void main(String[] args) throws Exception {
    var projectPath = Path.of("/home/alex/projects/feed4j");
    var buildConfig = readBuildConfig(projectPath);
    var outputPath = Path.of("out");
    var result = new Builder(buildConfig).build(projectPath.resolve("src"), outputPath);
    var jarPath = new JarPackager().packageJar(buildConfig.pkgConfig(), buildConfig.main(), result.classOutputPath(), outputPath);
    new ApplicationPackager().packageApplication(buildConfig.pkgConfig(), jarPath, outputPath, result.dependencyInfoSet());

    run(buildConfig.main(), result.classOutputPath(), result.dependencyInfoSet(), args);
  }

  private static void run(MainConfig mainConfig, Path appModulePath, Set<DependencyInfo> deps, String[] args) {
    new LayeredRunner().run(mainConfig.moduleName(), mainConfig.className(), appModulePath, deps.stream().map(directDep -> {
      var moduleDep = directDep.moduleDep();
      var paths = new ArrayList<Path>();
      paths.add(moduleDep.jarFile().toPath());
      moduleDep.deps().forEach(dep -> paths.add(dep.jarFile().toPath()));
      return new ModulePath(moduleDep.moduleName().name(), paths);
    }).toList(), args);
  }

  private static BuildConfig readBuildConfig(Path projectPath) throws IOException {
    var buildConfigFile = projectPath.resolve("jiggy.toml").toFile();
    var mapper = new TomlMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return mapper.reader().forType(BuildConfig.class).readValue(buildConfigFile);
  }
}
