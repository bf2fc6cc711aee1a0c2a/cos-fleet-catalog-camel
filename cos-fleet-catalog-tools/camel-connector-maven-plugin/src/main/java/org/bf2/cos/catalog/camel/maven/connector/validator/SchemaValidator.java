package org.bf2.cos.catalog.camel.maven.connector.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

@AutoService(Validator.class)
public final class SchemaValidator implements Validator {

    @Override
    public void validate(Context context, ObjectNode definition) {
        try (InputStream is = SchemaValidator.class.getResourceAsStream("/json-schema-2019-09.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
            JsonSchema jsonSchema = factory.getSchema(is);
            JsonNode schema = definition.requiredAt("/connector_type/schema");

            Collection<ValidationMessage> messages = jsonSchema.validate(schema);

            if (!messages.isEmpty()) {
                ObjectNode result = CatalogSupport.JSON_MAPPER.createObjectNode();

                result.with("connector")
                        .put("id", context.getConnector().getName());

                for (ValidationMessage message : jsonSchema.validate(schema)) {
                    result.withArray("violations").add(CatalogSupport.JSON_MAPPER.valueToTree(message));
                }

                String message = String.format(
                        "Failure processing connector: %s, reason: %s\n",
                        context.getConnector().getName(),
                        CatalogSupport.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));

                if (context.getMode() == Mode.WARN) {
                    context.getLog().warn(message);
                } else {
                    throw new ValidatorException(message);
                }
            }
        } catch (IOException e) {
            throw new ValidatorException(e);
        }
    }

    @Override
    public String toString() {
        return "SchemaValidator";
    }
}
