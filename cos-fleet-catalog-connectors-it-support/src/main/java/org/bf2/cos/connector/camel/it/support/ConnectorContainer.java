package org.bf2.cos.connector.camel.it.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.CaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StopContainerCmd;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public class ConnectorContainer extends GenericContainer<ConnectorContainer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorContainer.class);

    public static final String DEFAULT_APPLICATION_PROPERTIES_LOCATION = "/etc/camel/application.properties";
    public static final String DEFAULT_USER_PROPERTIES_LOCATION = "/etc/camel/conf.d/user.properties";
    public static final String DEFAULT_ROUTE_LOCATION = "/etc/camel/sources/route.yaml";

    public static final String CONTAINER_ALIAS = "tc-connector";
    public static final int DEFAULT_HTTP_PORT = 8080;
    public static final int GRACEFUL_STOP_TIMEOUT = 30;

    private Consumer<ConnectorContainer> customizer;
    private final List<Pair<String, byte[]>> files;
    private final Map<String, String> userProperties;

    public ConnectorContainer(String image) {
        this(DockerImageName.parse(image));
    }

    public ConnectorContainer(DockerImageName imageName) {
        super(imageName);

        this.files = new ArrayList<>();
        this.userProperties = new TreeMap<>();

        withEnv("QUARKUS_LOG_CONSOLE_JSON", "false");
        withEnv("CAMEL_K_MOUNT_PATH_CONFIGMAPS", "/etc/camel/conf.d/_configmaps");
        withEnv("CAMEL_K_MOUNT_PATH_SECRETS", "/etc/camel/conf.d/_secrets");
        withEnv("CAMEL_K_CONF_D", "/etc/camel/conf.d");
        withEnv("CAMEL_K_CONF", "/etc/camel/application.properties");

        withExposedPorts(DEFAULT_HTTP_PORT);
        waitingFor(WaitStrategies.forHealth(DEFAULT_HTTP_PORT));
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CONTAINER_ALIAS)));
    }

    public ConnectorContainer withCustomizer(Consumer<ConnectorContainer> customizer) {
        this.customizer = customizer;
        return self();

    }

    public ConnectorContainer withUserProperties(Map<String, String> properties) {
        this.userProperties.putAll(properties);
        return self();
    }

    public ConnectorContainer withUserProperty(String key, String format, Object... args) {
        this.userProperties.put(
                key,
                args.length == 0
                        ? format
                        : String.format(format, args));

        return self();
    }

    public ConnectorContainer withFile(String path, InputStream content) throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(content);

        return withFile(path, content.readAllBytes());
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
        return getHost();
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

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            Properties p = new Properties();

            try (InputStream ip = ConnectorContainer.class.getResourceAsStream("/integration-user.properties")) {
                p.load(ip);
            }

            p.putAll(this.userProperties);
            p.store(os, "user");

            copyFileToContainer(Transferable.of(os.toByteArray()), DEFAULT_USER_PROPERTIES_LOCATION);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void containerIsStopping(InspectContainerResponse containerInfo) {
        super.containerIsStopped(containerInfo);
        LOGGER.info("Container is stopping. Attempting to wait for graceful stop for {} seconds.", GRACEFUL_STOP_TIMEOUT);
        try (StopContainerCmd stopContainerCmd = getDockerClient()
                .stopContainerCmd(getContainerId())
                .withTimeout(GRACEFUL_STOP_TIMEOUT)) {
            stopContainerCmd.exec();
            LOGGER.info("Container was gracefully stopped.");
        } catch (Exception e) {
            LOGGER.error("Failed to gracefully stop container, this might lead to resource leaking.", e);
        }
    }

    public static Builder forDefinition(String definition) {
        return new Builder(definition);
    }

    public void withCamelComponentDebugEnv() {
        withEnv("quarkus.log.level", "DEBUG");
        withEnv("quarkus.log.category.\"org.apache.camel.component\".level", "DEBUG");
    }

    public static class Builder {
        private final Path definition;
        private final Map<String, Object> properties;
        private final Map<String, String> userProperties;

        private Network network;
        private String dlqKafkaTopic;
        private boolean simulateError;

        private Builder(String definition) {
            String root = System.getProperty("cos.catalog.definition.root");

            Objects.requireNonNull(definition);
            Objects.requireNonNull(root);

            if (!definition.startsWith(root)) {
                this.definition = Path.of(root, definition);
            } else {
                this.definition = Path.of(definition);
            }

            this.properties = new HashMap<>();
            this.userProperties = new HashMap<>();
        }

        public Builder withProperty(String key, String val) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(val);

            this.properties.put(key.trim(), val);
            return this;
        }

        public Builder withProperties(Map<String, Object> properties) {
            Objects.requireNonNull(properties);

            this.properties.putAll(properties);
            return this;
        }

        public Builder withUserProperty(String key, String val) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(val);

            this.userProperties.put(key.trim(), val);
            return this;
        }

        public Builder withUserProperties(Map<String, String> properties) {
            Objects.requireNonNull(properties);

            this.userProperties.putAll(properties);
            return this;
        }

        public Builder withLogCategory(String category, String level) {
            Objects.requireNonNull(category);
            Objects.requireNonNull(level);

            return withUserProperty(
                    String.format("quarkus.log.category.\"%s\".level", category),
                    level);
        }

        public Builder witNetwork(Network network) {
            this.network = network;
            return this;
        }

        public Builder withDlqErrorHandler(String dlqKafkaTopic, boolean simulateError) {
            this.dlqKafkaTopic = dlqKafkaTopic;
            this.simulateError = simulateError;
            return this;
        }

        public ConnectorContainer build() {
            try (InputStream is = Files.newInputStream(definition)) {
                ObjectMapper yaml = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

                ObjectMapper mapper = new ObjectMapper();
                JsonNode def = mapper.readValue(is, JsonNode.class);
                JsonNode meta = def.requiredAt("/channels/stable/shard_metadata");

                String image = meta.requiredAt("/connector_image").asText();
                DockerImageName imageName = DockerImageName.parse(image);

                ConnectorContainer answer = new ConnectorContainer(imageName);

                if (!properties.isEmpty()) {
                    String type = meta.requiredAt("/connector_type").asText();
                    String adapterKamelet = meta.requiredAt("/kamelets/adapter/name").asText();
                    String adapterPrefix = meta.requiredAt("/kamelets/adapter/prefix").asText();
                    String kafkaKamelet = meta.requiredAt("/kamelets/kafka/name").asText();
                    String kafkaPrefix = meta.requiredAt("/kamelets/kafka/prefix").asText();
                    String consumes = meta.requiredAt("/consumes").asText();
                    String produces = meta.requiredAt("/produces").asText();

                    ArrayNode integration = yaml.createArrayNode();
                    ObjectNode route = integration.addObject().with("route");
                    ObjectNode from = route.with("from");
                    ArrayNode steps = from.withArray("steps");
                    steps.addObject().with("to").put("uri", "log:before?showAll=true&multiline=true");

                    if (dlqKafkaTopic != null) {
                        integration.addObject().with("errorHandler").put("ref", "defaultErrorHandler");
                        answer.withUserProperties(
                                Map.of(
                                        "camel.beans.defaultErrorHandler",
                                        "#class:org.apache.camel.builder.DeadLetterChannelBuilder",
                                        "camel.beans.defaultErrorHandler.deadLetterUri",
                                        "kamelet:cos-kafka-not-secured-sink/errorHandler",
                                        "camel.kamelet.cos-kafka-not-secured-sink.errorHandler.bootstrapServers",
                                        properties.get("kafka_bootstrap_servers").toString(),
                                        "camel.kamelet.cos-kafka-not-secured-sink.errorHandler.topic",
                                        dlqKafkaTopic,
                                        "camel.beans.defaultErrorHandler.useOriginalMessage",
                                        "true"));
                    }

                    if (consumes != null) {
                        switch (consumes) {
                            case "application/json": {
                                ObjectNode step = steps.addObject();
                                step.with("to").put("uri", "kamelet:cos-decoder-json-action");
                                if (meta.has("consumes_class")) {
                                    step.with("to").with("parameters").set("contentClass", meta.get("consumes_class"));
                                }
                            }
                                break;
                            case "avro/binary": {
                                ObjectNode step = steps.addObject();
                                step.with("to").put("uri", "kamelet:cos-decoder-avro-action");
                                if (meta.has("consumes_class")) {
                                    step.with("to").with("parameters").set("contentClass", meta.get("consumes_class"));
                                }
                            }
                                break;
                            case "application/x-java-object": {
                                ObjectNode step = steps.addObject();
                                step.with("to").put("uri", "kamelet:cos-decoder-pojo-action");
                                if (meta.has("consumes_class")) {
                                    step.with("to").with("parameters").set("mimeType", meta.get("consumes_class"));
                                }
                            }
                                break;
                            case "text/plain":
                            case "application/octet-stream":
                                break;
                            default:
                                throw new IllegalArgumentException("Unsupported value format " + consumes);
                        }
                    }

                    configureProcessors(mapper, meta, steps, properties);

                    if (produces != null) {
                        switch (produces) {
                            case "application/json": {
                                ObjectNode step = steps.addObject();
                                step.with("to").put("uri", "kamelet:cos-encoder-json-action");
                                if (meta.has("consumes_class")) {
                                    step.with("to").with("parameters").set("contentClass", meta.get("consumes_class"));
                                }
                            }
                                break;
                            case "avro/binary": {
                                ObjectNode step = steps.addObject();
                                step.with("to").put("uri", "kamelet:cos-decoder-json-action");
                                if (meta.has("consumes_class")) {
                                    step.with("to").with("parameters").set("contentClass", meta.get("consumes_class"));
                                }
                            }
                                break;
                            case "text/plain": {
                                ObjectNode step = steps.addObject();
                                step.with("to").put("uri", "kamelet:cos-encoder-string-action");
                            }
                                break;
                            case "application/octet-stream": {
                                ObjectNode step = steps.addObject();
                                step.with("to").put("uri", "kamelet:cos-encoder-bytearray-action");
                            }
                                break;
                            default:
                                throw new IllegalArgumentException("Unsupported value format " + produces);
                        }
                    }

                    steps.addObject().with("removeHeader").put("name", "X-Content-Schema");
                    steps.addObject().with("removeProperty").put("name", "X-Content-Schema");
                    steps.addObject().with("to").put("uri", "log:debug?showAll=true&multiline=true");

                    if (simulateError) {
                        steps.addObject().with("bean").put("beanType",
                                "org.bf2.cos.connector.camel.processor.SimulateErrorProcessor");
                    }

                    ObjectNode to = yaml.createObjectNode();

                    switch (type) {
                        case "source":
                            from.put("uri", "kamelet:" + adapterKamelet);
                            to.put("uri", "kamelet:cos-kafka-not-secured-sink");

                            for (var entry : properties.entrySet()) {
                                if (entry.getKey().equals("processors")) {
                                    continue;
                                }
                                if (entry.getKey().equals("data_shape")) {
                                    continue;
                                }
                                if (entry.getKey().equals("error_handler")) {
                                    continue;
                                }

                                if (entry.getKey().startsWith(adapterPrefix + "_")) {
                                    String key = entry.getKey().substring(adapterPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    from.with("parameters").put(key, entry.getValue().toString());
                                }
                                if (entry.getKey().startsWith(kafkaPrefix + "_")) {
                                    String key = entry.getKey().substring(kafkaPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    to.with("parameters").put(key, entry.getValue().toString());
                                }
                            }

                            steps.addObject().set("to", to);

                            break;
                        case "sink":
                            from.put("uri", "kamelet:cos-kafka-not-secured-source");
                            to.put("uri", "kamelet:" + adapterKamelet);

                            for (var entry : properties.entrySet()) {
                                if (entry.getKey().equals("processors")) {
                                    continue;
                                }
                                if (entry.getKey().equals("data_shape")) {
                                    continue;
                                }
                                if (entry.getKey().equals("error_handler")) {
                                    continue;
                                }

                                if (entry.getKey().startsWith(kafkaPrefix + "_")) {
                                    String key = entry.getKey().substring(kafkaPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    from.with("parameters").put(key, entry.getValue().toString());
                                }
                                if (entry.getKey().startsWith(adapterPrefix + "_")) {
                                    String key = entry.getKey().substring(adapterPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    to.with("parameters").put(key, entry.getValue().toString());
                                }
                            }

                            steps.addObject().set("to", to);

                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported type: " + type);
                    }

                    // add this log to trace what happens after the message gets delivered
                    // to the target endpoint for troubleshooting purpose.
                    //
                    // i.e. the exchange will contain headers and properties added by the
                    // target system component
                    steps.addObject().with("to").put("uri", "log:after?showAll=true&multiline=true");

                    String routeYaml = yaml.writerWithDefaultPrettyPrinter().writeValueAsString(integration);

                    LOGGER.info("\n\n----------------\nroute: \n{}\n----------------\n\n", routeYaml);

                    answer.withFile(DEFAULT_ROUTE_LOCATION, routeYaml);

                    LOGGER.info("\n\n----------------\nuser properties: \n{}\n----------------\n\n", userProperties);
                    answer.withUserProperties(userProperties);

                    try (InputStream ip = ConnectorContainer.class.getResourceAsStream("/integration-application.properties")) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ip.transferTo(baos);
                        byte[] bytes = baos.toByteArray();

                        LOGGER.info("\n\n----------------\napplication properties: \n{}\n----------------\n\n",
                                new String(bytes));
                        answer.withFile(DEFAULT_APPLICATION_PROPERTIES_LOCATION, new ByteArrayInputStream(bytes));
                    }
                }

                if (this.network != null) {
                    answer.withNetwork(this.network);
                }

                return answer;

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private boolean configureProcessors(ObjectMapper mapper, JsonNode meta, ArrayNode steps,
                Map<String, Object> properties) {
            if (!properties.containsKey("processors")) {
                return false;
            }

            ArrayNode procs = mapper.convertValue(properties.get("processors"), ArrayNode.class);
            steps.addAll(procs);
            return true;
        }

    }
}
