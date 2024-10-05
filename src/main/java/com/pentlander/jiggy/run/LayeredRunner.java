package com.pentlander.jiggy.run;

import static java.util.Objects.requireNonNull;

import com.pentlander.jiggy.run.ModuleLayerBuilder.ModulePath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class LayeredRunner {
  public void run(String mainModuleName, String mainClassName, Path appModulePath, Collection<ModulePath> modulePaths, String[] args) {
    var layer = new ModuleLayerBuilder()
        .moduleName(mainModuleName)
        .qualifiedClassName(mainClassName)
        .modulePath(appModulePath)
        .depModulePaths(modulePaths)
        .build();
    MethodHandle mh;
    try {
      var clazz = layer.findLoader(mainModuleName).loadClass(mainClassName);
      mh = MethodHandles.lookup()
          .findStatic(clazz, "main", MethodType.methodType(void.class, String[].class));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    try {
      mh.invoke((Object) args);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    var mainModuleName = requireNonNull(System.getenv("MAIN_MODULE_NAME"));
    var mainClassName = requireNonNull(System.getenv("MAIN_CLASS_NAME"));
    var appModulePath = Path.of(requireNonNull(System.getenv("APP_MODULE_PATH")));
    var depModulesPath = Path.of(requireNonNull(System.getenv("DEP_MODULES_PATH")));
    List<ModulePath> depModulePaths;
    try (var pathStream = Files.list(depModulesPath)) {
      depModulePaths = pathStream.map(path -> {
        var moduleName = path.getFileName().toString();
        try (var jarPathStream = Files.list(path)) {
          return new ModulePath(moduleName, jarPathStream.toList());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    new LayeredRunner().run(mainModuleName, mainClassName, appModulePath, depModulePaths, args);
  }
}
