package com.pentlander.jiggy;

import static picocli.CommandLine.*;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.pentlander.jiggy.JiggyCommand.Run;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "jiggy", synopsisSubcommandLabel = "COMMAND", mixinStandardHelpOptions = true, subcommands = Run.class)
public class JiggyCommand implements Runnable {
  public static final String CLI_VERSION = "0.1";

  @Spec
  CommandSpec spec;

  @Option(names = {"-p", "--path"}, description = "Path to the project directory")
  Path projectPath = Path.of(".");

  @Override
  public void run() {
    throw new ParameterException(spec.commandLine(), "Missing required subcommand");
  }

  BuildConfig readBuildConfig() throws IOException {
      var buildConfigFile = projectPath.resolve("jiggy.toml").toFile();
      if (!buildConfigFile.exists()) {
          throw new ParameterException(spec.commandLine(), "Could not find jiggy.toml file in "
              + "project path: " + projectPath.toAbsolutePath().normalize());
      }
      var mapper = new TomlMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
      return mapper.reader().forType(BuildConfig.class).readValue(buildConfigFile);
  }

  @Command(name = "run", description = "Build and run the application", showEndOfOptionsDelimiterInUsageHelp = true)
  public static class Run implements Callable<Integer> {
    @ParentCommand
    JiggyCommand parent;

    @Parameters(description = "Parameters to forward to the application being run")
    List<String> parameters = List.of();

    @Override
    public Integer call() throws Exception {
      var projectPath = parent.projectPath;
      var buildConfig = parent.readBuildConfig();
      var pkgConfig = buildConfig.pkgConfig();

      var sourcePath = projectPath.resolve("src");
      var outputPath = projectPath.resolve("out");
      var result = new Builder(buildConfig).build(sourcePath, outputPath);
      var jarPath = new JarPackager(CLI_VERSION).packageJar(pkgConfig, buildConfig.main(),
          result.classOutputPath(), outputPath);
      var packager = new ApplicationPackager(jarPath, outputPath);
      var pkgResult = packager.packageDeps(result.dependencyInfoSet().values());
      var javaBinPath = packager.createImage();
      var mainConfig = buildConfig.main();
      packager.createLaunchScript(pkgConfig.name(), mainConfig);

      var modulePath = jarPath + ":" + pkgResult.depModulePath();
      var javaCmd = List.of(javaBinPath.toString(), "--module-path", modulePath, "--module",
          mainConfig.moduleName() + "/" + mainConfig.className());

      var cmd = new ArrayList<>(javaCmd);
      cmd.addAll(parameters);

      return new ProcessBuilder(cmd).inheritIO().start().waitFor();
    }
  }

  @Command(name = "clean", description = "Build and run the application")
  public static class Clean implements Callable<Integer> {
    @ParentCommand
    JiggyCommand parent;

    @Override
    public Integer call() throws Exception {
      var projectPath = parent.projectPath;
      deleteDir(projectPath.resolve("out"));
      return 0;
    }

    private static void deleteDir(Path dirPath) throws IOException {
      Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
          if (e == null) {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          } else {
            throw e;
          }
        }
      });
    }
  }
}
