package com.pentlander.jiggy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class LayeredRunner {
  private final ModuleLayer baseLayer = ModuleLayer.boot();
  private final ClassLoader classLoader = ClassLoader.getSystemClassLoader();

  void run(BuildConfig.MainConfig mainConfig, Path modulePath, Collection<DependencyInfo> deps) {
    var parentLayers = deps.stream().map(DependencyInfo::moduleDep).map(this::newModuleLayer).toList();
    var moduleFinder = ModuleFinder.of(modulePath);
    var mainModuleName = mainConfig.moduleName();
    var layerConfig = Configuration.resolve(moduleFinder, parentLayers.stream().map(ModuleLayer::configuration).toList(), ModuleFinder.of(), Set.of(mainModuleName));
    var layerController = ModuleLayer.defineModulesWithOneLoader(layerConfig, parentLayers, classLoader);
    var layer = layerController.layer();

    var mainPackage = mainConfig.className().substring(0, mainConfig.className().lastIndexOf("."));
    var thisModule = getClass().getModule();
    var mainModule = layer.findModule(mainModuleName).orElseThrow();
    layerController.addOpens(mainModule, mainPackage, thisModule);
    thisModule.addReads(mainModule);
    MethodHandle mh;
    try {
      var clazz = layer.findLoader(mainModuleName).loadClass(mainConfig.className());
      mh = MethodHandles.lookup()
          .findStatic(clazz, "main", MethodType.methodType(void.class, String[].class));
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    try {
      mh.invoke((Object) new String[]{});
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  ModuleLayer newModuleLayer(ModuleDep dep) {
    var paths = new ArrayList<Path>();
    paths.add(dep.jarFile().toPath());
    dep.deps().forEach(d -> paths.add(d.jarFile().toPath()));
    var moduleFinder = ModuleFinder.of(paths.toArray(new Path[]{}));
    var layerConfig = baseLayer.configuration().resolve(moduleFinder, ModuleFinder.of(), Set.of(dep.moduleName().name()));
    return baseLayer.defineModulesWithOneLoader(layerConfig, classLoader);
  }
}
