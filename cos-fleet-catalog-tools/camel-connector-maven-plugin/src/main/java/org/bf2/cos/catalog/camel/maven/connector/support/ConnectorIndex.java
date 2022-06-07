package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ConnectorIndex {
    private final TreeMap<String, ConnectorManifest> connectors;

    public ConnectorIndex() {
        this(Collections.emptyMap());
    }

    @JsonCreator
    public ConnectorIndex(
            @JsonProperty(value = "connectors") Map<String, ConnectorManifest> connectors) {
        this.connectors = new TreeMap<>();

        if (connectors != null) {
            this.connectors.putAll(connectors);
        }
    }

    public Map<String, ConnectorManifest> getConnectors() {
        return connectors;
    }

    public Optional<ConnectorManifest> lookup(String id) {
        return Optional.ofNullable(connectors.get(id));
    }
}
