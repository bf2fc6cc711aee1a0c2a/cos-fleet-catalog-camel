package org.bf2.cos.connector.camel.salesforce;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.extension.MetaDataExtension;
import org.apache.camel.component.salesforce.SalesforceComponent;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.bf2.cos.connector.camel.serdes.Serdes;
import org.bf2.cos.connector.camel.serdes.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;

public class SalesforceSchemaResolver implements Processor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesforceSchemaResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, JsonNode> schemas = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    private volatile SalesforceComponent component;
    private volatile MetaDataExtension extension;

    private String loginUrl;
    private String clientId;
    private String clientSecret;
    private String userName;
    private String password;

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        MetaDataExtension extension = getMetaDataExtension(exchange.getContext());
        if (extension == null) {
            LOGGER.debug("MetaDataExtension is not set");
            return;
        }

        final String objectName = exchange.getMessage().getHeader(SalesforceEndpointConfig.SOBJECT_NAME, String.class);
        final JsonNode objectSchema = schemas.computeIfAbsent(objectName, n -> computeSchema(extension, n));

        exchange.setProperty(Serdes.CONTENT_SCHEMA, objectSchema);
        exchange.setProperty(Serdes.CONTENT_SCHEMA_TYPE, Json.SCHEMA_TYPE);
    }

    private JsonNode computeSchema(MetaDataExtension extension, String objectName) {
        Map<String, Object> options = Map.of(
                SalesforceEndpointConfig.SOBJECT_NAME, objectName,
                "clientId", getClientId(),
                "clientSecret", getClientSecret(),
                "userName", getUserName(),
                "password", getPassword(),
                "loginUrl", getLoginUrl());

        return extension.meta(options)
                .map(MetaDataExtension.MetaData::getPayload)
                .map(ObjectSchema.class::cast)
                .flatMap(s -> getSchema(s, objectName))
                .map(s -> MAPPER.convertValue(s, JsonNode.class))
                .orElse(null);
    }

    private boolean isObjectSchema(ObjectSchema obj) {
        return !obj.getId().contains(":QueryRecords");
    }

    private Optional<ObjectSchema> getSchema(ObjectSchema obj, String objectName) {
        return obj.getOneOf().stream()
                .filter(ObjectSchema.class::isInstance)
                .map(ObjectSchema.class::cast)
                .filter(this::isObjectSchema)
                .peek(s -> {
                    s.set$schema("http://json-schema.org/draft-04/schema#");
                    s.setId("urn:jsonschema:salesforce:" + objectName);
                })
                .findFirst();
    }

    private MetaDataExtension getMetaDataExtension(CamelContext context) {
        if (this.extension == null) {
            synchronized (lock) {
                if (this.extension == null) {
                    this.component = new SalesforceComponent(context);
                    this.extension = component.getExtension(MetaDataExtension.class).orElse(null);
                }
            }
        }

        return this.extension;
    }
}
