package org.bf2.cos.catalog.camel.maven.connector.validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.service.AutoService;

import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityDifference;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

@AutoService(Validator.class)
public final class SchemaCompatibilityValidator implements Validator {
    private static final CompatibilityChecker CHECKER = new JsonSchemaCompatibilityChecker();

    @Override
    public void validate(Context context, ObjectNode definition) {
        try {
            String id = context.getConnector().getName().replace("-", "_");
            Path current = context.getCatalogPath().resolve(id + ".json");

            if (Files.exists(current)) {
                context.getLog().info("Reference schema file: " + current);

                JsonNode schema = definition.requiredAt("/connector_type/schema");
                JsonNode definitionReference = CatalogSupport.JSON_MAPPER.readValue(current.toFile(), JsonNode.class);
                JsonNode schemaReference = definitionReference.requiredAt("/connector_type/schema");

                String ref = CatalogSupport.JSON_MAPPER.writeValueAsString(schemaReference);
                String proposed = CatalogSupport.JSON_MAPPER.writeValueAsString(schema);

                CompatibilityExecutionResult compatibilityCheck = CHECKER.testCompatibility(
                        CompatibilityLevel.BACKWARD,
                        List.of(ref),
                        proposed);

                if (!compatibilityCheck.isCompatible()) {
                    ObjectNode result = CatalogSupport.JSON_MAPPER.createObjectNode();

                    result.with("connector")
                            .put("id", context.getConnector().getName());

                    for (CompatibilityDifference difference : compatibilityCheck.getIncompatibleDifferences()) {

                        RuleViolation violation = difference.asRuleViolation();

                        if (difference instanceof JsonSchemaCompatibilityDifference) {
                            Difference diff = ((JsonSchemaCompatibilityDifference) difference).getDifference();
                            result.withArray("violations").add(CatalogSupport.JSON_MAPPER.valueToTree(diff));
                        } else {
                            ObjectNode detail = result.withArray("violations").addObject();
                            detail.put("context", violation.getContext());
                            detail.put("description", violation.getDescription());
                        }
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
            }
        } catch (IOException e) {
            throw new ValidatorException(e);
        }
    }

    @Override
    public String toString() {
        return "SchemaCompatibilityValidator";
    }
}
