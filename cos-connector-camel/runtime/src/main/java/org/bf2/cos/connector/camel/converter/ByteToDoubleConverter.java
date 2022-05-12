package org.bf2.cos.connector.camel.converter;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.converter.ObjectConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Converter(generateBulkLoader = true)
public class ByteToDoubleConverter {
    private static final Logger LOG = LoggerFactory.getLogger(IOConverter.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private ByteToDoubleConverter() {
    }

    @Converter(order = 1)
    public static Double toDouble(byte[] bytes, Exchange exchange) throws IOException {
        return ObjectConverter.toDouble(IOConverter.toString(bytes, exchange));
    }
}
