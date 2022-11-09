package org.bf2.cos.connector.camel.actions;

import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

public class JsonPathProcessor implements Processor {
    public enum Action {
        EXTRACT,
        SET,
        REMOVE,
        RENAME
    }

    private final Configuration configuration;

    private Action action;
    //TODO: may need to eb sanitized
    private String expression;
    private String key;
    private Object value;
    private String newKey;

    public JsonPathProcessor() {
        this.configuration = Configuration.builder()
                .jsonProvider(new JacksonJsonNodeJsonProvider())
                .mappingProvider(new JacksonMappingProvider())
                .build();
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getNewKey() {
        return newKey;
    }

    public void setNewKey(String newKey) {
        this.newKey = newKey;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (action == null) {
            return;
        }

        JsonNode payload = exchange.getMessage().getMandatoryBody(JsonNode.class);
        DocumentContext document = JsonPath.using(configuration).parse(payload);

        switch (action) {
            case SET:
                document.put(
                        Objects.requireNonNull(expression),
                        Objects.requireNonNull(key),
                        Objects.requireNonNull(value));

                payload = document.json();
                break;
            case RENAME:
                document.renameKey(
                        Objects.requireNonNull(expression),
                        Objects.requireNonNull(key),
                        Objects.requireNonNull(newKey));

                payload = document.json();
                break;
            case REMOVE:
                document.delete(
                        Objects.requireNonNull(expression));

                payload = document.json();
                break;
            case EXTRACT:
                payload = document.read(
                        Objects.requireNonNull(expression));

                break;
        }

        exchange.getMessage().setBody(payload);
    }
}
