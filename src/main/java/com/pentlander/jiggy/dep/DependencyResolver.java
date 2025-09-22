package com.pentlander.jiggy.dep;

import com.pentlander.jiggy.BuildConfig.DependencyDesc;
import com.pentlander.jiggy.dep.ModuleDep.ModuleName;
import com.pentlander.jiggy.dep.ModuleDep.ModuleName.Automatic;
import com.pentlander.jiggy.dep.ModuleDep.ModuleName.NonModular;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

public class DependencyResolver {
  private final List<RemoteRepository> repos = List.of(
      new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build()
  );
  private final RepositorySystemSupplier repoSystemSupplier = new RepositorySystemSupplier();
  private final LocalRepository localRepo = new LocalRepository("local-repo");

  public Map<DependencyDesc, ModuleDep> resolve(List<DependencyDesc> depDescs) {
    var repoSystem = newRepoSystem();
    var session = newSession(repoSystem);

    var dependencies = depDescs.stream().map(desc -> {
      var coord = desc.coordinate();
      var resolvingArtifact = new DefaultArtifact(coord.groupId(), coord.artifactId(), coord.extension(), coord.version());
      return new Dependency(resolvingArtifact, null);
    }).toList();
    var collectRequest = new CollectRequest(dependencies, List.of(), repos);
    var dependencyRequest = new DependencyRequest(collectRequest, null);
    var resultModDeps = new LinkedHashMap<DependencyDesc, ModuleDep>();
    try {
      var dependencyResult = repoSystem.resolveDependencies(session, dependencyRequest);

      var directDepNodes = dependencyResult.getRoot().getChildren();
      for (int i = 0; i < directDepNodes.size(); i++) {
        var depNode = directDepNodes.get(i);
        var moduleDeps = new ArrayList<ModuleDep>();
        var listGen = new PreorderNodeListGenerator();
        depNode.accept(listGen);
        var artifacts = listGen.getArtifacts(false);
        for (var artifact : artifacts) {
          var file = artifact.getFile();
          if (file.getName().endsWith("jar")) {
            try (var jar = new JarFile(file)) {
              var moduleName = moduleName(jar);
              if (moduleName instanceof NonModular) {
                System.out.println("Not a modular jar");
              }

              var coordinate = new DependencyCoordinate(
                  artifact.getGroupId(),
                  artifact.getArtifactId(),
                  artifact.getExtension(),
                  artifact.getClassifier(),
                  artifact.getVersion());
              moduleDeps.add(new ModuleDep(coordinate, moduleName, file));
            }
          }
        }
        var resolvedModuleDep = moduleDeps.getFirst();
        var deps =
            moduleDeps.size() > 1 ? moduleDeps.subList(1, moduleDeps.size()) : List.<ModuleDep>of();
        var depDesc = depDescs.get(i);
        var modDep = new ModuleDep(
            depDesc.coordinate(),
            resolvedModuleDep.moduleName(),
            resolvedModuleDep.jarFile(),
            deps);
        resultModDeps.put(depDesc, modDep);
      }

      return resultModDeps;
    } catch (IOException | DependencyResolutionException e) {
      System.err.println(e);
      throw new RuntimeException(e);
    }
  }

  private static ModuleName moduleName(JarFile jar) {
    // If it's a modular jar, read the module descriptor from the module info
    var moduleInfoEntry = jar.getJarEntry("module-info.class");
    if (moduleInfoEntry != null) {
      try {
        var modDescriptor = ModuleDescriptor.read(jar.getInputStream(moduleInfoEntry));
        return new ModuleName.Explicit(modDescriptor.name());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    try {
      // Otherwise try to read the jar manifest and get the automatic module name
      var manifest = jar.getManifest();
      if (manifest == null) {
        return new NonModular();
      }
      return new Automatic(manifest.getMainAttributes().getValue("Automatic-Module-Name"));
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
