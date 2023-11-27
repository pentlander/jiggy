package com.pentlander.jiggy;

import java.io.File;
import java.lang.module.ModuleDescriptor;
import java.util.List;

public record ModuleDep(DependencyCoordinate coordinate, ModuleName moduleName, File jarFile, List<ModuleDep> deps) {
  ModuleDep(DependencyCoordinate coordinate, ModuleName moduleName, File jarFile) {
    this(coordinate, moduleName, jarFile, List.of());
  }

  public sealed interface ModuleName {
    String name();

    record Explicit(String name) implements ModuleName {}

    record Automatic(String name) implements ModuleName {}

    record NonModular() implements ModuleName {
      @Override
      public String name() {
        return null;
      }
    }
  }
}
