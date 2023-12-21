package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.MainConfig;
import com.pentlander.jiggy.BuildConfig.PackageConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarPackager {
  Path packageJar(PackageConfig pkgConfig, MainConfig mainConfig, Path explodedModulePath, Path outputPath) throws IOException {
    var manifest = new Manifest();
    var mainAttrs = manifest.getMainAttributes();
    mainAttrs.put(Name.MANIFEST_VERSION, "1.0");
    mainAttrs.put(new Name("Created-By"), "jiggy-packager:0.1");
    mainAttrs.put(Name.MAIN_CLASS, mainConfig.className());

    var jarPath = outputPath.resolve("%s-%s.jar".formatted(pkgConfig.name(), pkgConfig.version()));
    try (var outStream = Files.newOutputStream(jarPath);
        var jar = new JarOutputStream(outStream, manifest);
        var paths = Files.walk(explodedModulePath).skip(1)) {

      paths.forEach(path -> addFile(explodedModulePath, path, jar));
    }

    return jarPath;
  }

  private void addFile(Path basePath, Path filePath, JarOutputStream jar) throws UncheckedIOException {
    var relPath = basePath.relativize(filePath);
    try {
      var lastModifiedTime = Files.getLastModifiedTime(filePath);

      if (Files.isDirectory(filePath)) {
        var entryName = relPath.toString();
        if (!entryName.endsWith("/")) {
          entryName += "/";
        }

        var entry = new JarEntry(entryName)
            .setLastModifiedTime(lastModifiedTime);
        jar.putNextEntry(entry);
        jar.closeEntry();
      } else {
        var entry = new JarEntry(relPath.toString())
            .setLastModifiedTime(lastModifiedTime);
        jar.putNextEntry(entry);
        Files.copy(filePath, jar);
        jar.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
