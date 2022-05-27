package org.bf2.cos.catalog.camel.maven.connector.support;

public class Catalog {
    @Param(defaultValue = "${cos.catalog.path}")
    private String path;

    @Param(defaultValue = "${project.basedir}/src/generated/resources/META-INF/connectors")
    private String manifestsPath;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getManifestsPath() {
        return manifestsPath;
    }

    public void setManifestsPath(String manifestsPath) {
        this.manifestsPath = manifestsPath;
    }
}
