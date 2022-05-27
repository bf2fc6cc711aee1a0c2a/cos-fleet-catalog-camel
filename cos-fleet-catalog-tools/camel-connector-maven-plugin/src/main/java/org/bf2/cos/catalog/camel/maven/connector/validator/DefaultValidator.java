package org.bf2.cos.catalog.camel.maven.connector.validator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;

@AutoService(Validator.class)
public final class DefaultValidator implements Validator {
    @Override
    public void validate(Context context, ObjectNode definition) {
        final String type = definition.requiredAt("/channels/stable/shard_metadata/connector_type").asText();

        switch (type) {
            case "source":
                definition.requiredAt("/channels/stable/shard_metadata/produces");
                break;
            case "sink":
                definition.requiredAt("/channels/stable/shard_metadata/consumes");
                break;
            default:
                throw new ValidatorException("Unsupported connector type: " + type);
        }
    }

    @Override
    public String toString() {
        return "DefaultValidator";
    }
}
