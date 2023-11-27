package com.pentlander.jiggy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record DependencyCoordinate(String groupId, String artifactId, String version) {
  @JsonCreator
  public static DependencyCoordinate from(String coordinates) {
    var coordParts = coordinates.split(":");
    if (coordParts.length < 2) {
      throw new IllegalArgumentException("Expected at least 2 parts, found: " + coordParts.length);
    }

    var version = coordParts.length == 3 ? coordParts[2] : null;
    return new DependencyCoordinate(coordParts[0], coordParts[1], version);
  }

  @JsonValue
  @Override
  public String toString() {
    if (version != null) {
      return String.join(":", groupId, artifactId, version );
    }
    return String.join(":", groupId, artifactId);
  }

  public DependencyCoordinate versionless() {
    return new DependencyCoordinate(groupId, artifactId, null);
  }
}
