package com.pentlander.jiggy;

public record DependencyCoordinate(String groupId, String artifactId, String version) {
  static DependencyCoordinate from(String coordinates) {
    var coordParts = coordinates.split(":");
    if (coordParts.length != 3) {
      throw new IllegalArgumentException("Expected 3 parts, found: " + coordParts.length);
    }
    return new DependencyCoordinate(coordParts[0], coordParts[1], coordParts[2]);
  }

  @Override
  public String toString() {
    return String.join(":", groupId, artifactId, version);
  }
}
