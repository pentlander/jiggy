package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.DependencyDesc;
import com.pentlander.jiggy.BuildConfig.DependencyDesc.Extended;
import com.pentlander.jiggy.dep.DependencyResolver;
import com.pentlander.jiggy.dep.ModuleDep;
import com.pentlander.jiggy.dep.ModuleDep.ModuleName.Explicit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

public class Builder {
  private final List<DependencyDesc> dependencyDescs;

  public Builder(List<DependencyDesc> dependencyDescs) {
    this.dependencyDescs = dependencyDescs;
  }

  public Builder(BuildConfig buildConfig) {
    this(buildConfig.dependencies().compile());
  }

  Result build(Path sourcePath, Path outputPath) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();

    var depResolver = new DependencyResolver();
    var depInfoSet = depResolver.resolve(dependencyDescs);

    var modulesPath = outputPath.resolve("modules");
    var compileModulesPath = modulesPath.resolve("compile");
    var compileModulePaths = new ArrayList<String>();
    Files.createDirectories(compileModulesPath);
    for (var depInfo : depInfoSet.entrySet()) {
      var depDesc = depInfo.getKey();
      var directDep = depInfo.getValue();
      var depPath = directDep.jarFile().toPath();
      compileModulePaths.add(depPath.toString());
      if (!(directDep.moduleName() instanceof Explicit) && depDesc instanceof Extended extendedDesc) {
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
    var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
    var fileObjects = fileManager.getJavaFileObjectsFromPaths(filePaths);

    var options = new ArrayList<String>();
    options.add("-g"); // Generate debug info
    options.add("-d"); // Specify where to place generated class files
    var classOutputPath = outputPath.resolve("classes");
    options.add(classOutputPath.toString());
    options.add("--module-path");
    options.add(String.join(":", compileModulePaths));
    var task = compiler.getTask(null, fileManager, new DiagListener(), options, null, fileObjects);
    if (!task.call()) {
      throw new RuntimeException("Failed to compile.");
    }

    return new Result(classOutputPath, depInfoSet);
  }

  public record Result(Path classOutputPath, Map<DependencyDesc, ModuleDep> dependencyInfoSet) {}

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
