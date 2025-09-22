package com.pentlander.jiggy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    var projectPath = Path.of("/Users/alex/projects/feed4j");
    var buildConfig = readBuildConfig(projectPath);
    var outputPath = Path.of("out");
    var sourcePath = projectPath.resolve("src");

    var result = new Builder(buildConfig).build(sourcePath, outputPath);
    var jarPath = new JarPackager().packageJar(buildConfig.pkgConfig(), buildConfig.main(), result.classOutputPath(), outputPath);
    var mainConfig = buildConfig.main();
    var packager = new ApplicationPackager(outputPath);
    var pkgResult = packager.packageDeps(result.dependencyInfoSet().values());

    var javaBinPath = ProcessHandle.current().info().command().orElseThrow();
    var modulePath = jarPath + ":" + pkgResult.depModulePath();
    var handle =
        new ProcessBuilder(List.of(javaBinPath, "--module-path", modulePath, "--module",
            mainConfig.moduleName() + "/" + mainConfig.className())).inheritIO().start();
    System.exit(handle.waitFor());
  }

  private static BuildConfig readBuildConfig(Path projectPath) throws IOException {
    var buildConfigFile = projectPath.resolve("jiggy.toml").toFile();
    var mapper = new TomlMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return mapper.reader().forType(BuildConfig.class).readValue(buildConfigFile);
  }
}
