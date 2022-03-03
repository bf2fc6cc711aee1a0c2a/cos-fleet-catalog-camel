package org.bf2.cos.catalog.camel.maven.connector.validator;

import org.bf2.cos.catalog.camel.maven.connector.support.Connector;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;

@AutoService(Validator.class)
public final class DefaultValidator implements Validator {
    @Override
    public void validate(Connector connector, ObjectNode schema) {
        final String type = schema.requiredAt("/channels/stable/shard_metadata/connector_type").asText();

        switch (type) {
            case "source":
                schema.requiredAt("/channels/stable/shard_metadata/produces");
                break;
            case "sink":
                schema.requiredAt("/channels/stable/shard_metadata/consumes");
                break;
            default:
                throw new IllegalArgumentException("Unsupported connector type: " + type);
        }
    }

    @Override
    public String toString() {
        return "DefaultValidator";
    }
}
