/* Generated by camel build tools - do NOT edit this file! */
package org.bf2.cos.connector.camel.serdes.avro;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.DeferredContextBinding;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.SimpleTypeConverter;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.util.DoubleMap;

/**
 * Generated by camel build tools - do NOT edit this file!
 */
@SuppressWarnings("unchecked")
@DeferredContextBinding
public final class AvroTypeConverterLoader implements TypeConverterLoader, CamelContextAware {

    private CamelContext camelContext;

    public AvroTypeConverterLoader() {
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {
        registerConverters(registry);
    }

    private void registerConverters(TypeConverterRegistry registry) {
        addTypeConverter(registry, com.fasterxml.jackson.dataformat.avro.AvroSchema.class, com.fasterxml.jackson.databind.JsonNode.class, false,
            (type, exchange, value) -> org.bf2.cos.connector.camel.serdes.avro.AvroTypeConverter.toAvroSchema((com.fasterxml.jackson.databind.JsonNode) value));
        addTypeConverter(registry, com.fasterxml.jackson.dataformat.avro.AvroSchema.class, java.io.InputStream.class, false,
            (type, exchange, value) -> org.bf2.cos.connector.camel.serdes.avro.AvroTypeConverter.toAvroSchema((java.io.InputStream) value));
        addTypeConverter(registry, com.fasterxml.jackson.dataformat.avro.AvroSchema.class, java.lang.String.class, false,
            (type, exchange, value) -> org.bf2.cos.connector.camel.serdes.avro.AvroTypeConverter.toAvroSchema((java.lang.String) value));
        addTypeConverter(registry, java.lang.String.class, com.fasterxml.jackson.dataformat.avro.AvroSchema.class, false,
            (type, exchange, value) -> org.bf2.cos.connector.camel.serdes.avro.AvroTypeConverter.toString((com.fasterxml.jackson.dataformat.avro.AvroSchema) value));
        addTypeConverter(registry, org.apache.avro.Schema.class, com.fasterxml.jackson.databind.JsonNode.class, false,
            (type, exchange, value) -> org.bf2.cos.connector.camel.serdes.avro.AvroTypeConverter.toSchema((com.fasterxml.jackson.databind.JsonNode) value));
        addTypeConverter(registry, org.apache.avro.Schema.class, java.io.InputStream.class, false,
            (type, exchange, value) -> org.bf2.cos.connector.camel.serdes.avro.AvroTypeConverter.toSchema((java.io.InputStream) value));
        addTypeConverter(registry, org.apache.avro.Schema.class, java.lang.String.class, false,
            (type, exchange, value) -> org.bf2.cos.connector.camel.serdes.avro.AvroTypeConverter.toSchema((java.lang.String) value));
    }

    private static void addTypeConverter(TypeConverterRegistry registry, Class<?> toType, Class<?> fromType, boolean allowNull, SimpleTypeConverter.ConversionMethod method) { 
        registry.addTypeConverter(toType, fromType, new SimpleTypeConverter(allowNull, method));
    }

}
