package org.bf2.cos.catalog.camel.maven.connector.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

@AutoService(Validator.class)
public final class SchemaValidator implements Validator {

    @Override
    public void validate(Context context, ObjectNode schema) {
        try (InputStream is = SchemaValidator.class.getResourceAsStream("/json-schema-2019-09.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
            JsonSchema jsonSchema = factory.getSchema(is);
            JsonNode schemaNode = schema.requiredAt("/connector_type/schema");

            Collection<ValidationMessage> messages = jsonSchema.validate(schemaNode);

            if (!messages.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

                ObjectNode result = mapper.createObjectNode();

                result.with("connector")
                        .put("id", context.getConnector().getName());

                for (ValidationMessage message : jsonSchema.validate(schemaNode)) {
                    result.withArray("messages").add(mapper.valueToTree(message));
                }

                String message = String.format(
                        "Failure processing connector: %s, reason: %s\n",
                        context.getConnector().getName(),
                        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));

                if (context.getMode() == Mode.WARN) {
                    context.getLog().warn(message);
                } else {
                    throw new ValidatorException(message);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "SchemaValidator";
    }
}
