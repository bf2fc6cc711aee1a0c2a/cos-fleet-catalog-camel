package org.bf2.cos.connector.camel.it.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.github.dockerjava.api.command.InspectContainerResponse;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public class ConnectorContainer extends GenericContainer<ConnectorContainer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorContainer.class);

    public static final String DEFAULT_APPLICATION_PROPERTIES_LOCATION = "/etc/camel/application.properties";
    public static final String DEFAULT_USER_PROPERTIES_LOCATION = "/etc/camel/conf.d/user.properties";
    public static final String DEFAULT_ROUTE_LOCATION = "/etc/camel/sources/route.yaml";

    public static final String CONTAINER_ALIAS = "tc-connector";
    public static final int DEFAULT_HTTP_PORT = 8080;

    private Consumer<ConnectorContainer> customizer;
    private final List<Pair<String, byte[]>> files;
    private final Map<String, String> userProperties;

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
        waitingFor(Wait.forListeningPort());
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

    public static Builder forDefinition(String definition) {
        return new Builder(definition);
    }

    public static class Builder {
        private final String definition;
        private final Map<String, String> properties;

        private Network network;

        private Builder(String definition) {
            Objects.requireNonNull(definition);

            if (!definition.startsWith("/connectors/")) {
                definition = "/connectors/" + definition;
            }

            this.definition = definition;
            this.properties = new HashMap<>();
        }

        public Builder withProperty(String key, String val) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(val);

            this.properties.put(key, val);
            return this;
        }

        public Builder withProperties(Map<String, String> properties) {
            Objects.requireNonNull(properties);

            this.properties.putAll(properties);
            return this;
        }

        public Builder witNetwork(Network network) {
            this.network = network;
            return this;
        }

        public ConnectorContainer build() {
            try (InputStream is = ConnectorContainer.class.getResourceAsStream(definition)) {
                ObjectMapper yaml = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));

                ObjectMapper mapper = new ObjectMapper();
                JsonNode def = mapper.readValue(is, JsonNode.class);
                JsonNode meta = def.requiredAt("/channels/stable/shard_metadata");

                String image = meta.requiredAt("/connector_image").asText();
                String imageTag = System.getProperty("it.connector.container.tag");
                DockerImageName imageName = DockerImageName.parse(image).withTag(imageTag);

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
                    ArrayNode steps = route.withArray("steps");

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

                    steps.addObject().with("to").put("uri", "log:debug?showAll=true&multiline=true");

                    ObjectNode to = steps.addObject().with("to");

                    switch (type) {
                        case "source":
                            from.put("uri", "kamelet:" + adapterKamelet);
                            to.put("uri", "kamelet:cos-kafka-not-secured-sink");

                            for (var entry : properties.entrySet()) {
                                if (entry.getKey().startsWith(adapterPrefix + "_")) {
                                    String key = entry.getKey().substring(adapterPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    from.with("parameters").put(key, entry.getValue());
                                }
                                if (entry.getKey().startsWith(kafkaPrefix + "_")) {
                                    String key = entry.getKey().substring(kafkaPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    to.with("parameters").put(key, entry.getValue());
                                }
                            }
                            break;
                        case "sink":
                            from.put("uri", "kamelet:cos-kafka-not-secured-source");
                            to.put("uri", "kamelet:" + adapterKamelet);

                            for (var entry : properties.entrySet()) {
                                if (entry.getKey().startsWith(kafkaPrefix + "_")) {
                                    String key = entry.getKey().substring(kafkaPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    from.with("parameters").put(key, entry.getValue());
                                }
                                if (entry.getKey().startsWith(adapterPrefix + "_")) {
                                    String key = entry.getKey().substring(adapterPrefix.length());
                                    key = CaseUtils.toCamelCase(key, false, '_');

                                    to.with("parameters").put(key, entry.getValue());
                                }
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported type: " + type);
                    }

                    String routeYaml = yaml.writerWithDefaultPrettyPrinter().writeValueAsString(integration);

                    LOGGER.info("route: \n{}", routeYaml);

                    answer.withFile(DEFAULT_ROUTE_LOCATION, routeYaml);

                    try (InputStream ip = ConnectorContainer.class.getResourceAsStream("/integration-application.properties")) {
                        answer.withFile(DEFAULT_APPLICATION_PROPERTIES_LOCATION, ip);
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
    }
}
