package org.bf2.cos.connector.camel.it.support;

import java.nio.charset.StandardCharsets;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import com.github.dockerjava.api.command.InspectContainerResponse;

public class RedPandaKafkaContainer extends GenericContainer<RedPandaKafkaContainer> {
    public static final int KAFKA_PORT = 9092;
    public static final int KAFKA_OUTSIDE_PORT = 29092;
    public static final String CONTAINER_ALIAS = "tc-kafka";

    private static final String STARTER_SCRIPT = "/var/lib/redpanda/redpanda.sh";

    public RedPandaKafkaContainer() {
        super(ContainerImages.REDPANDA.imageName());

        withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("sh"));
        withCommand("-c", "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT);
        waitingFor(Wait.forLogMessage(".*Started Kafka API server.*", 1));
        withExposedPorts(KAFKA_PORT, KAFKA_OUTSIDE_PORT);
        withNetworkAliases(CONTAINER_ALIAS);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CONTAINER_ALIAS)));
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        super.containerIsStarting(containerInfo);

        final String addr = String.join(
                ",",
                "OUTSIDE://0.0.0.0:" + KAFKA_PORT,
                "PLAINTEXT://0.0.0.0:" + KAFKA_OUTSIDE_PORT);

        final String advAddr = String.join(
                ",",
                String.format("OUTSIDE://%s:%d", getHost(), getMappedPort(KAFKA_PORT)),
                String.format("PLAINTEXT://%s:%s", CONTAINER_ALIAS, KAFKA_OUTSIDE_PORT));

        String command = "#!/bin/bash\n";
        command += String.join(" ",
                "/usr/bin/rpk",
                "redpanda",
                "start",
                "--mode dev-container",
                "--kafka-addr",
                addr,
                "--advertise-kafka-addr",
                advAddr);

        //noinspection OctalInteger
        copyFileToContainer(
                Transferable.of(command.getBytes(StandardCharsets.UTF_8), 0777),
                STARTER_SCRIPT);
    }

    public String getBootstrapServers() {
        return String.format("PLAINTEXT://%s:%d", getHost(), getMappedPort(KAFKA_PORT));
    }

    public String getOutsideBootstrapServers() {
        return CONTAINER_ALIAS + ":" + KAFKA_OUTSIDE_PORT;
    }
}
