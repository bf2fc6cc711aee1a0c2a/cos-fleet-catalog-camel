package org.bf2.cos.catalog.camel.maven.connector.validator;

import org.bf2.cos.catalog.camel.maven.connector.support.Connector;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface Validator {
    void validate(Connector connector, ObjectNode schema);
}
