package com.pentlander.jiggy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.IOException;
import java.nio.file.Path;

public class Main {
  public static void main(String[] args) throws Exception {
    var projectPath = Path.of("/home/alex/projects/feed4j");
    var buildConfig = readBuildConfig(projectPath);
    var result = new Builder(buildConfig).build(projectPath.resolve("src"), Path.of("out"));

    new LayeredRunner().run(buildConfig.main(), result.classOutputPath(), result.dependencyInfoSet());
  }

  private static BuildConfig readBuildConfig(Path projectPath) throws IOException {
    var buildConfigFile = projectPath.resolve("jiggy.toml").toFile();
    var mapper = new TomlMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return mapper.reader().forType(BuildConfig.class).readValue(buildConfigFile);
  }
}
