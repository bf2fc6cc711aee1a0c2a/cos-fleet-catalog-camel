package org.bf2.cos.connector.camel.serdes.schema;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.util.ObjectHelper;
import org.bf2.cos.connector.camel.serdes.MimeType;
import org.bf2.cos.connector.camel.serdes.avro.AvroSchemaResolver;
import org.bf2.cos.connector.camel.serdes.json.JsonSchemaResolver;

/**
 * Schema resolver processor delegates to either Avro or Json schema resolver based on the given mimetype property.
 * When mimetype is of type application/x-java-object uses additional target mimetype (usually the produces mimetype) to
 * determine the schema resolver (Avro or Json).
 * Delegates to schema resolver and sets proper content class and schema properties on the delegate.
 */
public class DelegatingSchemaResolver implements Processor {
    private String mimeType;
    private String targetMimeType;

    private String schema;
    private String contentClass;

    @Override
    public void process(Exchange exchange) throws Exception {
        if (ObjectHelper.isEmpty(mimeType)) {
            return;
        }

        MimeType mimeType = MimeType.of(this.mimeType);
        Processor resolver;
        if (mimeType.equals(MimeType.JAVA_OBJECT)) {
            if (ObjectHelper.isEmpty(targetMimeType)) {
                return;
            }
            resolver = fromMimeType(MimeType.of(targetMimeType));
        } else {
            resolver = fromMimeType(mimeType);
        }

        if (resolver != null) {
            resolver.process(exchange);
        }
    }

    private Processor fromMimeType(MimeType mimeType) {
        switch (mimeType) {
            case AVRO:
            case AVRO_STRUCT:
                AvroSchemaResolver avroSchemaResolver = new AvroSchemaResolver();
                avroSchemaResolver.setSchema(this.schema);
                avroSchemaResolver.setContentClass(this.contentClass);
                return avroSchemaResolver;
            case JSON:
            case STRUCT:
                JsonSchemaResolver jsonSchemaResolver = new JsonSchemaResolver();
                jsonSchemaResolver.setSchema(this.schema);
                jsonSchemaResolver.setContentClass(this.contentClass);
                return jsonSchemaResolver;
            default:
                return null;
        }
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getContentClass() {
        return contentClass;
    }

    public void setContentClass(String contentClass) {
        this.contentClass = contentClass;
    }

    public String getTargetMimeType() {
        return targetMimeType;
    }

    public void setTargetMimeType(String targetMimeType) {
        this.targetMimeType = targetMimeType;
    }
}
