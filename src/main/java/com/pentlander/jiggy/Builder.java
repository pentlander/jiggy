package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.DependencyDesc.Extended;
import com.pentlander.jiggy.ModuleDep.ModuleName.Explicit;
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
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
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
      var dep = depInfo.moduleDep();
      var depPath = dep.jarFile().toPath();
      compileModulePaths.add(depPath.toString());
      if (!(dep.moduleName() instanceof Explicit) && depInfo.dependencyDesc() instanceof Extended extendedDesc) {
        var exportDescs = Set.copyOf(extendedDesc.transitive());
        dep.deps()
            .stream()
            .filter(transitiveDep -> exportDescs.contains(transitiveDep.coordinate().versionless()))
            .forEach(transitiveDep -> compileModulePaths.add(transitiveDep.jarFile().getPath()));
      }
      Files.copy(depPath, compileModulesPath.resolve(depPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }


    var filePaths = new ArrayList<Path>();
    Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
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
    options.add("-g");
    options.add("-d");
    var classOutputPath = outputPath.resolve("classes");
    options.add(classOutputPath.toString());
    options.add("--module-path");
//    options.add(deps.stream().map(dep -> dep.jarFile().getPath()).collect(Collectors.joining(":")));
    options.add(String.join(":", compileModulePaths));
    var task = compiler.getTask(null, fileManager, new DiagListener(), options, null, fileObjects);
    if (!task.call()) {
      throw new RuntimeException("Failed to compile.");
    }

    return new Result(classOutputPath, depInfoSet);
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
