package com.pentlander.jiggy.project;

import java.nio.file.Path;

public record ProjectModuleInfo(String moduleName, Path sourcePath, Path classOutputPath) {}
