package com.pentlander.jiggy.project;

import java.io.IOException;

public interface ProjectBuild {
  default void preCompile(ProjectModuleInfo projectModuleInfo) throws IOException {}

  default void postCompile(ProjectModuleInfo projectModuleInfo) throws IOException {}
}
