package com.pentlander.jiggy;

import picocli.CommandLine;

public class Main {
  public static void main(String[] args) {
    int exitCode = new CommandLine(new JiggyCommand()).execute(args);
    System.exit(exitCode);
  }
}
