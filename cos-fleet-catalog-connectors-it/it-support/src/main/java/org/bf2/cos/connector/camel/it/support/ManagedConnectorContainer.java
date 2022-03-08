package org.bf2.cos.connector.camel.it.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

public class ManagedConnectorContainer extends GenericContainer<ManagedConnectorContainer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedConnectorContainer.class);
    private static final int DEFAULT_HTTP_PORT = 8080;

    public ManagedConnectorContainer() {
        super(System.getProperty("connector.container.image").trim());

        addEnv("QUARKUS_LOG_CONSOLE_JSON", "false");
        withLogConsumer(new Slf4jLogConsumer(LOGGER));
        withExposedPorts(DEFAULT_HTTP_PORT);
        waitingFor(Wait.forListeningPort());
    }

    public String getServiceAddress() {
        return getContainerIpAddress();
    }

    public int getServicePort() {
        return getMappedPort(DEFAULT_HTTP_PORT);
    }
}
