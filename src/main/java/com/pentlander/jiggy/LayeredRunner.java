package com.pentlander.jiggy;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class LayeredRunner {
  private final ModuleLayer baseLayer = ModuleLayer.boot();
  private final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

  void run(String mainModuleName, String mainClassName, Path appModulePath, Collection<ModulePath> modulePaths, String[] args) {
    var parentLayers = modulePaths.stream().map(this::newModuleLayer).toList();
    var moduleFinder = ModuleFinder.of(appModulePath);
    var layerConfig = Configuration.resolve(moduleFinder, parentLayers.stream().map(ModuleLayer::configuration).toList(), ModuleFinder.of(), Set.of(mainModuleName));
    var layerController = ModuleLayer.defineModulesWithOneLoader(layerConfig, parentLayers, classLoader);
    var layer = layerController.layer();

    var mainPackage = mainClassName.substring(0, mainClassName.lastIndexOf("."));
    var thisModule = getClass().getModule();
    var mainModule = layer.findModule(mainModuleName).orElseThrow();
    layerController.addOpens(mainModule, mainPackage, thisModule);
    thisModule.addReads(mainModule);
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

  record ModulePath(String moduleName, List<Path> jarPaths) {}

  ModuleLayer newModuleLayer(ModulePath modulePath) {
    var moduleFinder = ModuleFinder.of(modulePath.jarPaths().toArray(new Path[]{}));
    var layerConfig = baseLayer.configuration().resolve(moduleFinder, ModuleFinder.of(), Set.of(modulePath.moduleName()));
    return baseLayer.defineModulesWithOneLoader(layerConfig, classLoader);
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
