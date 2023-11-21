package com.pentlander.jiggy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

public class LayeredRunner {
  private final ModuleLayer baseLayer = ModuleLayer.boot();
  private final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

  void run(BuildConfig.MainConfig mainConfig, Path explodedModulePath, Collection<DependencyResolver.ModuleDep> deps) {
    var paths = new Path[deps.size() + 1];
    paths[0] = explodedModulePath;
    int i = 1;
    for (var dep : deps) {
      paths[i++] = dep.jarFile().toPath();
    }
    var moduleFinder = ModuleFinder.of(paths);
    var mainModule = mainConfig.moduleName();
    var layerConfig = baseLayer.configuration()
        .resolve(moduleFinder, ModuleFinder.of(), Set.of(mainModule));
    var layer = baseLayer.defineModulesWithOneLoader(layerConfig, classLoader);
    MethodHandle mh;
    try {
      var clazz = layer.findLoader(mainModule).loadClass(mainConfig.className());
      mh = MethodHandles.publicLookup().findStatic(clazz, "main", MethodType.methodType(void.class, String[].class));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    try {
      mh.invoke((Object) new String[]{});
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
