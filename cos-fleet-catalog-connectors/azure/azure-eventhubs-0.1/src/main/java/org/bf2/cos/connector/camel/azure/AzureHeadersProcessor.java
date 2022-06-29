package org.bf2.cos.connector.camel.azure;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;

public class AzureHeadersProcessor {
    public void process(Exchange exchange, TypeConverter converter) throws Exception {
        exchange.getMessage().getHeaders().replaceAll(
                (k, v) -> {
                    if (k.startsWith("Camel")) {
                        return v;
                    }

                    return converter.convertTo(String.class, v);
                });

    }
}
