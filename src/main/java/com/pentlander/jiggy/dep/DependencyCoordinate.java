package com.pentlander.jiggy.dep;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record DependencyCoordinate(String groupId, String artifactId, String extension, String classifier, String version) {
  @JsonCreator
  public static DependencyCoordinate from(String coords) {
    var parts = coords.split(":");
    if (parts.length < 2 || parts.length > 5) {
      throw new IllegalArgumentException("Bad artifact coordinates " + coords
          + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
    }
    var groupId = parts[0];
    var artifactId = parts[1];

    String version = null;
    if (parts.length > 2) {
      version = parts[2];
    }
    var extension = "jar";
    if (parts.length > 3) {
      extension = parts[3];
    }
    var classifier = "";
    if (parts.length > 4) {
      extension = parts[4];
    }
    return new DependencyCoordinate(groupId, artifactId, extension, classifier, version);
  }

  @JsonValue
  @Override
  public String toString() {
    var joiner = new StringJoiner(":");
    joiner.add(groupId).add(artifactId).add(extension);

    if (classifier != null && !classifier.isEmpty()) {
      joiner.add(classifier);
    }
    if (version != null && !version.isEmpty()) {
      joiner.add(version);
    }

    return joiner.toString();
  }

  public DependencyCoordinate versionless() {
    return new DependencyCoordinate(groupId, artifactId, extension, classifier, null);
  }
}
