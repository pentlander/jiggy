package com.pentlander.jiggy;

import static picocli.CommandLine.*;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.pentlander.jiggy.JiggyCommand.Run;
import java.io.IOException;
import java.nio.file.Path;
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

      var sourcePath = projectPath.resolve("src");
      var outputPath = projectPath.resolve("out");
      var result = new Builder(buildConfig).build(sourcePath, outputPath);
      var jarPath = new JarPackager(CLI_VERSION).packageJar(buildConfig.pkgConfig(), buildConfig.main(),
          result.classOutputPath(), outputPath);
      var mainConfig = buildConfig.main();
      var packager = new ApplicationPackager(outputPath);
      var pkgResult = packager.packageDeps(result.dependencyInfoSet().values());

      var javaBinPath = ProcessHandle.current().info().command().orElseThrow();
      var modulePath = jarPath + ":" + pkgResult.depModulePath();
      var javaCmd = List.of(javaBinPath, "--module-path", modulePath, "--module",
          mainConfig.moduleName() + "/" + mainConfig.className());

      var cmd = new ArrayList<>(javaCmd);
      cmd.addAll(parameters);

      return new ProcessBuilder(cmd).inheritIO().start().waitFor();
    }
  }
}
