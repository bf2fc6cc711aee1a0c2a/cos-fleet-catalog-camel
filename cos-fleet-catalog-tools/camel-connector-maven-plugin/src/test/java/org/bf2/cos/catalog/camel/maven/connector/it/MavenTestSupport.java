package org.bf2.cos.catalog.camel.maven.connector.it;

import java.io.InputStream;

import org.bf2.cos.catalog.camel.maven.connector.support.CatalogSupport;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class MavenTestSupport {

    static ObjectNode adapter(String type, String inOut, String mimeType) {
        ObjectNode adapter = CatalogSupport.JSON_MAPPER.createObjectNode();

        adapter.with("metadata")
                .with("labels")
                .put("camel.apache.org/kamelet.type", type);

        if (inOut != null && mimeType != null) {
            adapter.with("spec")
                    .with("types")
                    .with(inOut)
                    .put("mediaType", mimeType);
        }

        return adapter;
    }

    public static String asKey(String value) {
        return CatalogSupport.asKey(value);
    }

    public static InputStream loadKamelet(String name) {
        return MavenTestSupport.class.getResourceAsStream("/kamelets/" + name + ".kamelet.yaml");
    }
}
