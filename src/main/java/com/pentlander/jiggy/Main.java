package com.pentlander.jiggy;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.pentlander.jiggy.DependencyResolver.ModuleDep;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;

public class Main {
  public static void main(String[] args) throws Exception {
    var projectPath = Path.of("/home/alex/projects/feed4j");
    var compiler = ToolProvider.getSystemJavaCompiler();
    var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);

    var buildConfig = readBuildConfig(projectPath);
    var depResolver = new DependencyResolver();
    var deps = new LinkedHashSet<ModuleDep>();
    for (String dependency : buildConfig.dependencies().compile()) {
      deps.addAll(depResolver.resolve(DependencyCoordinate.from(dependency)));
    }

    var outputPath = Path.of("out");
    var modulesPath = outputPath.resolve("modules");
    Files.createDirectories(modulesPath);
    for (var dep : deps) {
      var depPath = dep.jarFile().toPath();
      Files.copy(depPath, modulesPath.resolve(depPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    var filePaths = new ArrayList<Path>();
    Files.walkFileTree(projectPath.resolve("src"), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (file.getFileName().toString().endsWith(".java")) {
          filePaths.add(file);
        }
        return FileVisitResult.CONTINUE;
      }
    });
    var fileObjects = fileManager.getJavaFileObjectsFromPaths(filePaths);

    var options = new ArrayList<String>();
    options.add("-d");
    var classOutputPath = outputPath.resolve("classes");
    options.add(classOutputPath.toString());
    options.add("--module-path");
    options.add(deps.stream().map(dep -> dep.jarFile().getPath()).collect(Collectors.joining(":")));
//    options.add(modulesPath.toString());
    var task = compiler.getTask(null, fileManager, null, options, null, fileObjects);
    task.call();

    new LayeredRunner().run(buildConfig.main(), classOutputPath, deps);
  }

  private static BuildConfig readBuildConfig(Path projectPath) throws IOException {
    var buildConfigFile = projectPath.resolve("jiggy.toml").toFile();
    var mapper = new TomlMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    return mapper.reader().forType(BuildConfig.class).readValue(buildConfigFile);
  }
}
