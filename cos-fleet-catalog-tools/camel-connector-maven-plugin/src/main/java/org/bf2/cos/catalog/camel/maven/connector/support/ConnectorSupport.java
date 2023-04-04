package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ConnectorSupport {
    private ConnectorSupport() {
    }

    public static Collection<ConnectorDependency> dependencies(
            Log log,
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

        for (ObjectNode node : kamelets) {
            log.info("kamelet: " + node.at("/metadata/name").asText());

            for (JsonNode depNode : node.at("/spec/dependencies")) {

                final String dep = depNode.asText();
                final ConnectorDependency result;

                if (dep.startsWith("mvn:")) {
                    String[] coords = dep.substring("mvn:".length()).split(":");

                    if (Objects.equals(coords[0], "org.apache.camel.kamelets")
                            && Objects.equals(coords[1], "camel-kamelets-utils")) {
                        continue;
                    }

                    log.info(">> " + node.at("/metadata/name").asText());

                    result = new ConnectorDependency(
                            coords[0],
                            coords[1],
                            coords[2]);

                } else if (dep.startsWith("camel:")) {
                    String coord = dep.substring("camel:".length());
                    result = new ConnectorDependency(
                            "org.apache.camel.quarkus",
                            "camel-quarkus-" + coord,
                            camelQuarkusVersion);

                } else if (dep.startsWith("github:")) {
                    String[] coords = dep.substring("github:".length()).split(":");
                    result = new ConnectorDependency(
                            "com.github." + coords[0],
                            coords[1],
                            coords[2]);
                } else {
                    throw new MojoExecutionException("Unsupported dependency: " + dep);
                }

                log.info(">> " + result.toString());
                dependencies.add(result);

            }
        }

        return dependencies;
    }
}
