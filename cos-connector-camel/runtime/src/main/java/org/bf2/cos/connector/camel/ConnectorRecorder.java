package org.bf2.cos.connector.camel;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.apache.camel.CamelContext;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListener;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.microprofile.health.ConnectorHealthCheckRegistry;
import org.apache.camel.spi.CamelContextCustomizer;

@Recorder
public class ConnectorRecorder {
    public RuntimeValue<CamelContextCustomizer> createContextCustomizer(ConnectorConfig config) {
        return new RuntimeValue<>(new CamelContextCustomizer() {

            @Override
            public void configure(CamelContext camelContext) {
                configureHealthCheckRegistry(camelContext);
                camelContext.setStreamCaching(false);
            }

            /*
             * explicitly configure the health check registry as a workaround
             * for https://issues.apache.org/jira/browse/CAMEL-18617
             */
            private void configureHealthCheckRegistry(CamelContext camelContext) {
                HealthCheckRegistry registry = new ConnectorHealthCheckRegistry(camelContext);
                registry.setId("camel-microprofile-health");
                registry.setEnabled(true);

                camelContext.setExtension(HealthCheckRegistry.class, registry);
            }
        });
    }

    public RuntimeValue<MainListener> createMainCustomizer(ConnectorConfig config) {
        return new RuntimeValue<>(new MainListenerSupport() {
            @Override
            public void beforeInitialize(BaseMainSupport main) {
                main.addOverrideProperty("camel.health.registryEnabled", "false");
                main.configure().health().setRegistryEnabled(false);

                main.getCamelContext().setStreamCaching(false);
                main.configure().setStreamCachingEnabled(false);
            }
        });
    }
}
