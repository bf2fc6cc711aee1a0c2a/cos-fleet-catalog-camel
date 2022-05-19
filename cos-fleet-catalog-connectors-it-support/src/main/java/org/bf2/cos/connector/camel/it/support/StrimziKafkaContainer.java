package org.bf2.cos.connector.camel.it.support;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import com.github.dockerjava.api.command.InspectContainerResponse;

public class StrimziKafkaContainer extends io.strimzi.test.container.StrimziKafkaContainer {
    public static final int KAFKA_PORT = 9092;
    public static final int KAFKA_OUTSIDE_PORT = 29092;
    public static final String CONTAINER_ALIAS = "tc-kafka";

    public StrimziKafkaContainer() {
        super("quay.io/strimzi-test-container/test-container:0.101.0-kafka-3.1.0");

        withNetworkAliases(CONTAINER_ALIAS);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CONTAINER_ALIAS)));
        withBrokerId(1);
        withKraft();
        waitForRunning();
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        super.containerIsStarting(containerInfo);
    }

    @Override
    public String getBootstrapServers() {
        return super.getBootstrapServers();
    }

    public String getOutsideBootstrapServers() {
        return CONTAINER_ALIAS + ":" + KAFKA_PORT;
    }
}
