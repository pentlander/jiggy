package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.DependencyDesc.Extended;
import com.pentlander.jiggy.ModuleDep.ModuleName.Explicit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public class Builder {
  private final BuildConfig buildConfig;

  public Builder(BuildConfig buildConfig) {
    this.buildConfig = buildConfig;
  }

  Result build(Path sourcePath, Path outputPath) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);

    var depResolver = new DependencyResolver();
    var depInfoSet = new LinkedHashSet<DependencyInfo>();
    for (var dependency : buildConfig.dependencies().compile()) {
      var moduleDep = depResolver.resolve(dependency.coordinate());
      depInfoSet.add(new DependencyInfo(dependency, moduleDep));
    }

    var modulesPath = outputPath.resolve("modules");
    var compileModulesPath = modulesPath.resolve("compile");
    var compileModulePaths = new ArrayList<String>();
    Files.createDirectories(compileModulesPath);
    for (var depInfo : depInfoSet) {
      var directDep = depInfo.moduleDep();
      var depPath = directDep.jarFile().toPath();
      compileModulePaths.add(depPath.toString());
      if (!(directDep.moduleName() instanceof Explicit) && depInfo.dependencyDesc() instanceof Extended extendedDesc) {
        var exportDescs = Set.copyOf(extendedDesc.transitive());
        directDep.deps()
            .stream()
            .filter(dep -> exportDescs.contains(dep.coordinate().versionless()))
            .forEach(dep -> compileModulePaths.add(dep.jarFile().getPath()));
      }
      Files.copy(depPath, compileModulesPath.resolve(depPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }


    List<Path> filePaths;
    try (var files = Files.walk(sourcePath)) {
      filePaths = files.filter(path -> path.toString().endsWith(".java")).toList();
    }
    var fileObjects = fileManager.getJavaFileObjectsFromPaths(filePaths);

    var options = new ArrayList<String>();
    options.add("-g");
    options.add("-d");
    var classOutputPath = outputPath.resolve("classes");
    options.add(classOutputPath.toString());
    options.add("--module-path");
    options.add(String.join(":", compileModulePaths));
    var task = compiler.getTask(null, fileManager, new DiagListener(), options, null, fileObjects);
    if (!task.call()) {
      throw new RuntimeException("Failed to compile.");
    }

    addLayeredRunner(classOutputPath);
    return new Result(classOutputPath, depInfoSet);
  }

  private void addLayeredRunner(Path outputPath) throws IOException {
    var resourcePath = Paths.get(LayeredRunner.class.getName().replace('.', '/') + ".class");
    var classFileBytes = Objects.requireNonNull(getClass().getClassLoader()
        .getResourceAsStream(resourcePath.toString())).readAllBytes();
    var filePath = outputPath.resolve(resourcePath);
    Files.createDirectories(filePath.getParent());
    Files.write(outputPath.resolve(resourcePath), classFileBytes);
  }

  public record Result(Path classOutputPath, Set<DependencyInfo> dependencyInfoSet) {}

  static class DiagListener implements DiagnosticListener<JavaFileObject> {
    @Override
    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
      if (diagnostic.getKind().equals(Kind.ERROR)) {
        System.out.println("Code: " + diagnostic.getCode());
      }
      System.err.println(diagnostic);
    }
  }
}
