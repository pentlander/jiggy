package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.DependencyDesc;
import com.pentlander.jiggy.BuildConfig.DependencyDesc.Extended;
import com.pentlander.jiggy.dep.DependencyInfo;
import com.pentlander.jiggy.dep.DependencyResolver;
import com.pentlander.jiggy.dep.ModuleDep.ModuleName.Explicit;
import com.pentlander.jiggy.run.LayeredRunner;
import com.pentlander.jiggy.run.ModuleLayerBuilder.ModulePath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

public class Builder {
  private final Iterable<DependencyDesc> dependencyDescs;

  public Builder(Iterable<DependencyDesc> dependencyDescs) {
    this.dependencyDescs = dependencyDescs;
  }

  public Builder(BuildConfig buildConfig) {
    this(buildConfig.dependencies().compile());
  }

  Result build(Path sourcePath, Path outputPath) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);

    // Resolve all the
    var depResolver = new DependencyResolver();
    var depInfoSet = new LinkedHashSet<DependencyInfo>();
    for (var dependency : dependencyDescs) {
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

  public record Result(Path classOutputPath, Set<DependencyInfo> dependencyInfoSet) {
    List<ModulePath> modulePaths() {
      return dependencyInfoSet.stream().map(directDep -> {
        var moduleDep = directDep.moduleDep();
        var paths = new ArrayList<Path>();
        paths.add(moduleDep.jarFile().toPath());
        moduleDep.deps().forEach(dep -> paths.add(dep.jarFile().toPath()));
        return new ModulePath(moduleDep.moduleName().name(), paths);
      }).toList();
    }
  }

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
