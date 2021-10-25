package org.bf2.cos.connector.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.spi.CamelContextCustomizer;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ConnectorRecorder {
    public RuntimeValue<CamelContextCustomizer> createContextCustomizer(ConnectorConfig config) {
        return new RuntimeValue<>(new ConnectorContextCustomizer(config));
    }

    public static class ConnectorContextCustomizer implements CamelContextCustomizer {
        private final ConnectorConfig config;

        public ConnectorContextCustomizer(ConnectorConfig config) {
            this.config = config;
        }

        @Override
        public void configure(CamelContext camelContext) {
            camelContext.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");
        }
    }
}
