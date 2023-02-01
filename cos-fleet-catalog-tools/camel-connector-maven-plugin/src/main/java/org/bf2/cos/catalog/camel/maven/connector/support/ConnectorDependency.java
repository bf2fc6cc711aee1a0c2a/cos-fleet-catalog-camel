package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.Objects;

public class ConnectorDependency {
    public final String groupId;
    public final String artifactId;
    public final String version;

    public ConnectorDependency(String groupId, String artifactId) {
        this.groupId = Objects.requireNonNull(groupId);
        this.artifactId = Objects.requireNonNull(artifactId);
        this.version = null;
    }

    public ConnectorDependency(String groupId, String artifactId, String version) {
        this.groupId = Objects.requireNonNull(groupId);
        this.artifactId = Objects.requireNonNull(artifactId);
        this.version = Objects.requireNonNull(version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectorDependency)) {
            return false;
        }

        ConnectorDependency artifact = (ConnectorDependency) o;

        return Objects.equals(groupId, artifact.groupId)
                && Objects.equals(artifactId, artifact.artifactId)
                && Objects.equals(version, artifact.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return version != null
                ? groupId + ":" + artifactId + ":" + version
                : groupId + ":" + artifactId;
    }

}
