package com.pentlander.jiggy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.pentlander.jiggy.BuildConfig.DependencyDesc.Coordinate;
import com.pentlander.jiggy.BuildConfig.DependencyDesc.Extended;
import java.io.IOException;
import java.util.List;

public record BuildConfig(@JsonProperty("package") PackageConfig pkgConfig, MainConfig main, DependenciesConfig dependencies) {
  public record PackageConfig(String name, String version, String description, String javaVersion) {}
  public record MainConfig(@JsonProperty("module") String moduleName, @JsonProperty("class") String className) {}
  public record DependenciesConfig(List<DependencyDesc> compile, List<DependencyDesc> test) {}

  @JsonDeserialize(using = DependencyDescDeserializer.class)
  public sealed interface DependencyDesc {
    DependencyCoordinate coordinate();

    @JsonDeserialize
    record Coordinate(DependencyCoordinate coordinate) implements DependencyDesc {}
    @JsonDeserialize
    record Extended(DependencyCoordinate coordinate, List<DependencyCoordinate> transitive) implements DependencyDesc {}
  }

  public static class DependencyDescDeserializer extends JsonDeserializer<DependencyDesc> {
    @Override
    public DependencyDesc deserialize(JsonParser jsonParser,
        DeserializationContext context) throws IOException {
      var node = context.readTree(jsonParser);

      if (node.isTextual()) {
        return new Coordinate(DependencyCoordinate.from(node.asText()));
      }

      return context.readTreeAsValue(node, Extended.class);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
        TypeDeserializer typeDeserializer) throws IOException {
      System.out.println("Is this called");
      return super.deserializeWithType(p, ctxt, typeDeserializer);
    }
  }
}
