package org.bf2.cos.connector.camel.processor;

import io.quarkus.runtime.util.ExceptionUtil;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;

public class AddExceptionHeaderProcessor implements Processor {
    @Override
    public void process(Exchange exchange) {
        RuntimeException exception = (RuntimeException) exchange.getProperty(ExchangePropertyKey.EXCEPTION_CAUGHT);
        if (exception != null) {
            String generatedStackTrace = ExceptionUtil.generateStackTrace(exception);
            exchange.getMessage().setHeader("rhoc.error-cause", generatedStackTrace);
        }
    }
}
