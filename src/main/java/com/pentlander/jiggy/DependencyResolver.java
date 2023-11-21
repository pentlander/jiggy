package com.pentlander.jiggy;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.LocalRepositoryProvider;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;

public class DependencyResolver {
  private final List<RemoteRepository> repos = List.of(
      new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()
  );
  private final RepositorySystemSupplier repoSystemSupplier = new RepositorySystemSupplier();
  private final LocalRepository localRepo = new LocalRepository("local-repo");

  public List<ModuleDep> resolve(DependencyCoordinate depCoordinate) {
    var artifact = new DefaultArtifact(depCoordinate.groupId(), depCoordinate.artifactId(), "jar", depCoordinate.version());
    var repoSystem = newRepoSystem();
    var session = newSession(repoSystem);

    var dependency = new Dependency(artifact, null);
    var collectRequest = new CollectRequest(dependency, repos);
    var dependencyRequest = new DependencyRequest(collectRequest, null);
    try {
      var moduleDeps = new ArrayList<ModuleDep>();
      var dependencyResult = repoSystem.resolveDependencies(session, dependencyRequest);
      for (var artifactResult : dependencyResult.getArtifactResults()) {
        var art = artifactResult.getArtifact();
        System.out.println(art.getArtifactId());
        var file = art.getFile();
        try (var jar = new JarFile(file)) {
          var moduleName = moduleName(jar);
          if (moduleName == null) {
            System.out.println("Not a modular jar");
          }
          moduleDeps.add(new ModuleDep(moduleName, file));
        }
      }
      System.out.println();
      return moduleDeps;
    } catch (IOException | DependencyResolutionException e) {
      System.err.println(e);
      return List.of();
    }
  }

  public record ModuleDep(String moduleName, File jarFile) {}

  private static String moduleName(JarFile jar) {
    var moduleInfoEntry = jar.getJarEntry("module-info.class");
    if (moduleInfoEntry != null) {
      try {
        var modDescriptor = ModuleDescriptor.read(jar.getInputStream(moduleInfoEntry));
        return modDescriptor.name();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    try {
      var manifest = jar.getManifest();
      if (manifest == null) {
        return null;
      }
      return manifest.getMainAttributes().getValue("Automatic-Module-Name");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private RepositorySystem newRepoSystem() {
    return repoSystemSupplier.get();
  }

  private RepositorySystemSession newSession(RepositorySystem repoSystem) {
    var session = MavenRepositorySystemUtils.newSession();
    session.setLocalRepositoryManager(repoSystem.newLocalRepositoryManager(session, localRepo));
    return session;
  }
}
