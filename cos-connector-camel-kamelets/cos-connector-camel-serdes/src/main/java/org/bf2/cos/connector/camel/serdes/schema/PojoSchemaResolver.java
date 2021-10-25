package org.bf2.cos.connector.camel.serdes.schema;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;
import org.bf2.cos.connector.camel.serdes.MimeType;
import org.bf2.cos.connector.camel.serdes.avro.AvroSchemaResolver;
import org.bf2.cos.connector.camel.serdes.json.JsonSchemaResolver;

public class PojoSchemaResolver implements Processor {
    private MimeType mimeType;
    private Processor resolver;

    public String getMimeType() {
        return mimeType.type();
    }

    public void setMimeType(String mimeType) {
        if (ObjectHelper.isEmpty(mimeType)) {
            return;
        }

        this.mimeType = MimeType.of(mimeType);

        switch (this.mimeType) {
            case AVRO:
                this.resolver = new AvroSchemaResolver();
                break;
            case JSON:
                this.resolver = new JsonSchemaResolver();
                break;
            default:
                throw new IllegalArgumentException("Unsupported mime type: " + mimeType);
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (resolver != null) {
            resolver.process(exchange);
        }
    }
}
