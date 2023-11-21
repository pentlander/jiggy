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

  exports com.pentlander.jiggy;
}
