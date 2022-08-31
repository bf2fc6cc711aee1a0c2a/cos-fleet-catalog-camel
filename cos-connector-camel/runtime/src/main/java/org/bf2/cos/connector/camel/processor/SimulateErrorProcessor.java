package org.bf2.cos.connector.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;

public class SimulateErrorProcessor {
    public void process(Exchange exchange, TypeConverter converter) throws Exception {
        throw new RuntimeException("too bad");
    }
}
