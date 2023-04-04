package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.Objects;

public class ConnectorDependency {
    public final String groupId;
    public final String artifactId;
    public final String version;

    public ConnectorDependency(String groupId, String artifactId, String version) {
        this.groupId = Objects.requireNonNull(groupId);
        this.artifactId = Objects.requireNonNull(artifactId);
        this.version = Objects.requireNonNull(version);
    }

    public ConnectorDependency(String groupId, String artifactId) {
        this.groupId = Objects.requireNonNull(groupId);
        this.artifactId = Objects.requireNonNull(artifactId);
        this.version = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ConnectorDependency)) {
            return false;
        }

        ConnectorDependency artifatc = (ConnectorDependency) o;
        return Objects.equals(groupId, artifatc.groupId)
                && Objects.equals(artifactId, artifatc.artifactId)
                && Objects.equals(version, artifatc.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
