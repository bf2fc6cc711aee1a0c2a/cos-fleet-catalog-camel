package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Manifest {
    private volatile boolean changed;
    private int revision;
    private final TreeSet<String> dependencies;
    private String image;
    private String baseImage;

    @JsonCreator
    public Manifest(
            @JsonProperty(value = "revision") Integer revision,
            @JsonProperty(value = "dependencies") Collection<String> dependencies,
            @JsonProperty(value = "image") String image,
            @JsonProperty(value = "baseImage") String baseImage) {

        this.changed = false;
        this.image = image;
        this.baseImage = baseImage;
        this.revision = revision == null ? 0 : revision;

        this.dependencies = new TreeSet<>();
        if (dependencies != null) {
            this.dependencies.addAll(dependencies);
        }
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
        if (!(o instanceof Manifest)) {
            return false;
        }

        Manifest manifest = (Manifest) o;

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
