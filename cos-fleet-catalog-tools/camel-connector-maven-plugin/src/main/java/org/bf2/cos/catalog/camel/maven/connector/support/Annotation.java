package org.bf2.cos.catalog.camel.maven.connector.support;

import org.apache.maven.plugins.annotations.Parameter;

public class Annotation {

    @Parameter
    private String name;
    @Parameter
    private String value;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
