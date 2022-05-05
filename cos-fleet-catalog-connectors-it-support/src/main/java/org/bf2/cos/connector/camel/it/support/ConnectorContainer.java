package org.bf2.cos.connector.camel.it.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import com.github.dockerjava.api.command.InspectContainerResponse;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public class ConnectorContainer extends GenericContainer<ConnectorContainer> {
    public static final String CONTAINER_ALIAS = "tc-connector";
    public static final int DEFAULT_HTTP_PORT = 8080;

    private Consumer<ConnectorContainer> customizer;
    private final List<Pair<String, byte[]>> files;

    public ConnectorContainer() {
        this(System.getProperty("connector.container.image").trim());
    }

    public ConnectorContainer(String format, Object... args) {
        this(String.format(format, args));
    }

    public ConnectorContainer(String group, String image, String tag) {
        this(
                Objects.requireNonNull(group)
                        + "/"
                        + Objects.requireNonNull(image)
                        + ":"
                        + Objects.requireNonNull(tag));
    }

    public ConnectorContainer(String image) {
        super(image);

        this.files = new ArrayList<>();

        withEnv("QUARKUS_LOG_CONSOLE_JSON", "false");
        withEnv("CAMEL_K_MOUNT_PATH_CONFIGMAPS", "/etc/camel/conf.d/_configmaps");
        withEnv("CAMEL_K_MOUNT_PATH_SECRETS", "/etc/camel/conf.d/_secrets");
        withEnv("CAMEL_K_CONF_D", "/etc/camel/conf.d");
        withEnv("CAMEL_K_CONF", "/etc/camel/application.properties");

        withExposedPorts(DEFAULT_HTTP_PORT);
        waitingFor(Wait.forListeningPort());
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CONTAINER_ALIAS)));
    }

    public ConnectorContainer withCustomizer(Consumer<ConnectorContainer> customizer) {
        this.customizer = customizer;
        return self();

    }

    public ConnectorContainer withFile(String path, byte[] content) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);

        this.files.add(new ImmutablePair<>(path, content));

        return self();
    }

    public ConnectorContainer withFile(String path, String content) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);

        this.files.add(new ImmutablePair<>(path, content.getBytes(StandardCharsets.UTF_8)));

        return self();
    }

    public String getServiceAddress() {
        return getContainerIpAddress();
    }

    public int getServicePort() {
        return getMappedPort(DEFAULT_HTTP_PORT);
    }

    public RequestSpecification getRequest() {
        return RestAssured.given()
                .baseUri("http://" + getServiceAddress())
                .port(this.getServicePort());
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        super.containerIsStarting(containerInfo);

        if (this.customizer != null) {
            this.customizer.accept(this);
        }

        for (Pair<String, byte[]> file : files) {
            copyFileToContainer(Transferable.of(file.getRight()), file.getLeft());
        }
    }
}
