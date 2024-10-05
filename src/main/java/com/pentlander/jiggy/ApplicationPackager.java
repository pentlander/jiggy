package com.pentlander.jiggy;

import com.pentlander.jiggy.BuildConfig.PackageConfig;
import com.pentlander.jiggy.dep.DependencyInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Objects;

public class ApplicationPackager {
  public void packageApplication(
      PackageConfig pkgConfig, Path jarPath, Path outputPath, Collection<DependencyInfo> deps)  {
    var appOutputPath = outputPath.resolve("application");
    deps.parallelStream().forEach(depInfo -> {
      var directDep = depInfo.moduleDep();
      var moduleName = Objects.requireNonNull(directDep.moduleName().name());
      var depDir = appOutputPath.resolve(moduleName);
      try {
        Files.createDirectories(depDir);
        var filePath = directDep.jarFile().toPath();
        Files.copy(filePath, depDir.resolve(filePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        for (var dep : directDep.deps()) {
          var depFilePath = dep.jarFile().toPath();
          Files.copy(depFilePath, depDir.resolve(depFilePath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

//    var jlink = ToolProvider.findFirst("jlink").orElseThrow();
//    var jdkModules = new ArrayList<String>();
//    jlink.run(System.out, System.err, );
  }
}
