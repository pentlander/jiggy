package com.pentlander.jiggy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.pentlander.jiggy.run.LayeredRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public class Main {
  public static void main(String[] args) throws Exception {
    var projectPath = Path.of("/Users/alex/projects/feed4j");
    var buildConfig = readBuildConfig(projectPath);
    var outputPath = Path.of("out");
    var sourcePath = projectPath.resolve("src");

    var buildDeps = Objects.requireNonNullElse(buildConfig.dependencies().build(), List.<BuildConfig.DependencyDesc>of());
//    var buildScriptRunner = new BuildScriptRunner(buildConfig.pkgConfig().name(), buildDeps, sourcePath, outputPath);
//    buildScriptRunner.resolveBuildScript();

    var result = new Builder(buildConfig).build(sourcePath, outputPath);
    var jarPath = new JarPackager().packageJar(buildConfig.pkgConfig(), buildConfig.main(), result.classOutputPath(), outputPath);
    new ApplicationPackager().packageApplication(buildConfig.pkgConfig(), jarPath, outputPath, result.dependencyInfoSet());

    var mainConfig = buildConfig.main();
    new LayeredRunner().run(mainConfig.moduleName(), mainConfig.className(), result.classOutputPath(), result.modulePaths(), args);
  }

  private static BuildConfig readBuildConfig(Path projectPath) throws IOException {
    var buildConfigFile = projectPath.resolve("jiggy.toml").toFile();
    var mapper = new TomlMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return mapper.reader().forType(BuildConfig.class).readValue(buildConfigFile);
  }
}
