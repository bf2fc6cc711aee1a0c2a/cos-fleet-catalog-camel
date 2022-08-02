package org.bf2.cos.connector.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jackson.JacksonConstants;
import org.apache.camel.spi.CamelContextCustomizer;
import org.bf2.cos.connector.camel.languages.EagerRefLanguage;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ConnectorRecorder {

    public RuntimeValue<CamelContextCustomizer> createContextCustomizer(ConnectorConfig config) {
        return new RuntimeValue<>(new ConnectorContextCustomizer(config));
    }

    public RuntimeValue<CamelContextCustomizer> createRuntimeContextCustomizer(ConnectorConfig config) {
        return new RuntimeValue<>(new ConnectorRuntimeContextCustomizer(config));
    }

    public static class ConnectorContextCustomizer implements CamelContextCustomizer {
        private final ConnectorConfig config;

        public ConnectorContextCustomizer(ConnectorConfig config) {
            this.config = config;
        }

        @Override
        public void configure(CamelContext camelContext) {
            // WARNING: this is clearly an hack !!!!
            //
            // The default RefLanguage component lazily resolve the ref expression
            // hence it does not work with kamelets as the route template engine
            // leverages a ThreadLocal bean repository to reify the route template,
            // then such repo si cleared out and not more known by the routing engine.
            //
            // This EagerRefLanguage perform an eager registry look-up to resolve
            // the expression (aka, it happens at reify time) as a workaround.

            camelContext.getRegistry().bind("ref", new EagerRefLanguage());
        }
    }

    public static class ConnectorRuntimeContextCustomizer implements CamelContextCustomizer {
        private final ConnectorConfig config;

        public ConnectorRuntimeContextCustomizer(ConnectorConfig config) {
            this.config = config;
        }

        @Override
        public void configure(CamelContext camelContext) {
            camelContext.getGlobalOptions().put(JacksonConstants.ENABLE_TYPE_CONVERTER, "true");

            //
            // Since camel 3.17, the stream caching is enabled by default on CamelContext
            // however it does not play very well with the current connector set-up so
            // we have to temporarily disable it
            //
            // For more info see:
            // - https://camel.apache.org/manual/camel-3x-upgrade-guide-3_17.html#_stream_caching
            //
            camelContext.setStreamCaching(false);
        }
    }
}
