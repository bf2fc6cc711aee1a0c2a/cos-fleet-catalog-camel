package org.bf2.cos.catalog.camel.maven.connector.validator;

import org.apache.maven.plugin.logging.Log;
import org.bf2.cos.catalog.camel.maven.connector.support.Connector;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface Validator {
    void validate(Context context, ObjectNode schema);

    enum Mode {
        WARN,
        FAIL
    }

    interface Context {
        Connector getConnector();

        Log getLog();

        Mode getMode();
    }
}
