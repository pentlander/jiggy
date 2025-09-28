module com.pentlander.jiggy {
  requires org.apache.maven.resolver;
  requires org.apache.maven.resolver.impl;
  requires org.apache.maven.resolver.supplier;
  requires org.apache.maven.resolver.util;
  requires maven.resolver.provider;

  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.dataformat.toml;

  requires java.compiler;
  requires java.net.http;
  requires info.picocli;

  exports com.pentlander.jiggy;
  exports com.pentlander.jiggy.project;
  exports com.pentlander.jiggy.dep;

  opens com.pentlander.jiggy to info.picocli;
}
