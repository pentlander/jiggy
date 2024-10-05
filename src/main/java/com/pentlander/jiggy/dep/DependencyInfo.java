package com.pentlander.jiggy.dep;

import com.pentlander.jiggy.BuildConfig.DependencyDesc;

public record DependencyInfo(DependencyDesc dependencyDesc, ModuleDep moduleDep) {}
