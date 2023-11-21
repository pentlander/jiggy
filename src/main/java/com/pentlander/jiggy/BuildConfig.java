package com.pentlander.jiggy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BuildConfig(MainConfig main, DependenciesConfig dependencies) {
  public record MainConfig(@JsonProperty("module") String moduleName, @JsonProperty("class") String className) {}
  public record DependenciesConfig(List<String> compile, List<String> test) {}
}
