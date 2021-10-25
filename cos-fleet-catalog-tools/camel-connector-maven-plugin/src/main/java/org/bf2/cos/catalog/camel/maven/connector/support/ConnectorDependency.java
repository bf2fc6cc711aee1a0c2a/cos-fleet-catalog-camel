package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.Objects;

public class ConnectorDependency {
    public final String groupId;
    public final String artifactiId;
    public final String version;

    public ConnectorDependency(String groupId, String artifactiId, String version) {
        this.groupId = Objects.requireNonNull(groupId);
        this.artifactiId = Objects.requireNonNull(artifactiId);
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

        ConnectorDependency artifatc = (ConnectorDependency) o;
        return Objects.equals(groupId, artifatc.groupId)
                && Objects.equals(artifactiId, artifatc.artifactiId)
                && Objects.equals(version, artifatc.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactiId, version);
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactiId + ":" + version;
    }
}
