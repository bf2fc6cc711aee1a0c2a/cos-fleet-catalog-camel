package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConnectorManifest {
    private volatile boolean changed;
    private int revision;
    private final TreeSet<String> dependencies;
    private final TreeSet<String> types;
    private String image;
    private String baseImage;
    private String catalog;

    @JsonCreator
    public ConnectorManifest(
            @JsonProperty(value = "catalog") String catalog,
            @JsonProperty(value = "revision") Integer revision,
            @JsonProperty(value = "dependencies") Collection<String> dependencies,
            @JsonProperty(value = "image") String image,
            @JsonProperty(value = "baseImage") String baseImage,
            @JsonProperty(value = "types") Collection<String> types) {

        this.catalog = catalog;
        this.changed = false;
        this.image = image;
        this.baseImage = baseImage;
        this.revision = revision == null ? 0 : revision;

        this.dependencies = new TreeSet<>();
        if (dependencies != null) {
            this.dependencies.addAll(dependencies);
        }

        this.types = new TreeSet<>();
        if (types != null) {
            this.types.addAll(types);
        }
    }

    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getBaseImage() {
        return baseImage;
    }

    public void setBaseImage(String baseImage) {
        this.baseImage = baseImage;
    }

    public int getRevision() {
        return revision;
    }

    public TreeSet<String> getDependencies() {
        return dependencies;
    }

    public TreeSet<String> getTypes() {
        return types;
    }

    public void bump() {
        if (!this.changed) {
            this.revision = this.revision + 1;
            this.changed = true;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectorManifest)) {
            return false;
        }

        ConnectorManifest manifest = (ConnectorManifest) o;

        return getRevision() == manifest.getRevision()
                && Objects.equals(getDependencies(), manifest.getDependencies());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getRevision(),
                getDependencies());
    }
}
