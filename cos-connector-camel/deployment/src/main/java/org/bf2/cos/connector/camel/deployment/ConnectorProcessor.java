package org.bf2.cos.connector.camel.deployment;

import org.apache.camel.quarkus.core.deployment.main.spi.CamelMainListenerBuildItem;
import org.apache.camel.quarkus.core.deployment.spi.CamelContextCustomizerBuildItem;
import org.bf2.cos.connector.camel.ConnectorConfig;
import org.bf2.cos.connector.camel.ConnectorRecorder;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class ConnectorProcessor {

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelContextCustomizerBuildItem customizeContext(ConnectorRecorder recorder, ConnectorConfig config) {
        return new CamelContextCustomizerBuildItem(recorder.createContextCustomizer(config));
    }

    @Record(ExecutionTime.STATIC_INIT)
    @BuildStep
    CamelMainListenerBuildItem customizeMain(ConnectorRecorder recorder, ConnectorConfig config) {
        return new CamelMainListenerBuildItem(recorder.createMainCustomizer(config));
    }
}
