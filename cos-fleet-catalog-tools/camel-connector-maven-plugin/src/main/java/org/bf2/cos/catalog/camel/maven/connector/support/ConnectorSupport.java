package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ConnectorSupport {
    private ConnectorSupport() {
    }

    public static Collection<ConnectorDependency> dependencies(
            KameletsCatalog catalog,
            Connector connector,
            String camelQuarkusVersion) throws MojoExecutionException {

        final Set<ConnectorDependency> dependencies = new HashSet<>();

        List<ObjectNode> kamelets = new ArrayList<>();

        kamelets.add(catalog.kamelet(
                connector.getAdapter().getName(),
                connector.getAdapter().getVersion()));
        kamelets.add(catalog.kamelet(
                connector.getKafka().getName(),
                connector.getKafka().getVersion()));

        if (connector.getActions() != null) {
            for (Connector.ActionRef ref : connector.getActions()) {
                kamelets.add(catalog.kamelet(
                        ref.getName(),
                        ref.getVersion()));
            }
        }

        for (ObjectNode node : kamelets) {
            for (JsonNode depNode : node.at("/spec/dependencies")) {

                final String dep = depNode.asText();

                if (dep.startsWith("mvn:")) {
                    String[] coords = dep.substring("mvn:".length()).split(":");
                    dependencies.add(new ConnectorDependency(
                            coords[0],
                            coords[1],
                            coords[2]));
                } else if (dep.startsWith("camel:")) {
                    String coord = dep.substring("camel:".length());
                    dependencies.add(new ConnectorDependency(
                            "org.apache.camel.quarkus",
                            "camel-quarkus-" + coord,
                            camelQuarkusVersion));
                } else if (dep.startsWith("github:")) {
                    String[] coords = dep.substring("github:".length()).split(":");
                    dependencies.add(new ConnectorDependency(
                            "com.github." + coords[0],
                            coords[1],
                            coords[2]));
                } else {
                    throw new MojoExecutionException("Unsupported dependency: " + dep);
                }
            }
        }

        return dependencies;
    }
}
