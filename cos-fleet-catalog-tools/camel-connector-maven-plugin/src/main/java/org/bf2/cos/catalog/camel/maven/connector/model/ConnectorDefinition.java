package org.bf2.cos.catalog.camel.maven.connector.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorDefinition {

    @JsonProperty("connector_type")
    private ConnectorType connectorType = new ConnectorType();

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Channel> channels = new TreeMap<>();

    public ConnectorType getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(ConnectorType connectorType) {
        this.connectorType = connectorType;
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }

    public void setChannels(Map<String, Channel> channels) {
        this.channels = channels;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Channel {
        @JsonProperty("shard_metadata")
        private Metadata metadata = new Metadata();

        public Metadata getMetadata() {
            return metadata;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ConnectorType {
        @JsonProperty("id")
        private String id;

        @JsonProperty(value = "kind", defaultValue = "ConnectorType")
        private String kind = "ConnectorType";

        @JsonProperty("icon_href")
        private String iconRef;

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("version")
        private String version;

        @JsonProperty("labels")
        private Set<String> labels = new TreeSet<>();

        @JsonProperty("capabilities")
        private Set<String> capabilities = new TreeSet<>();

        @JsonProperty("channels")
        private Set<String> channels = new TreeSet<>();

        @JsonProperty("schema")
        private ObjectNode schema;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getIconRef() {
            return iconRef;
        }

        public void setIconRef(String iconRef) {
            this.iconRef = iconRef;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Set<String> getLabels() {
            return labels;
        }

        public void setLabels(Set<String> labels) {
            this.labels = labels;
        }

        public Set<String> getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(Set<String> capabilities) {
            this.capabilities = capabilities;
        }

        public Set<String> getChannels() {
            return channels;
        }

        public void setChannels(Set<String> channels) {
            this.channels = channels;
        }

        public ObjectNode getSchema() {
            return schema;
        }

        public void setSchema(ObjectNode schema) {
            this.schema = schema;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Metadata {
        @JsonProperty("connector_revision")
        private String connectorRevision;

        @JsonProperty("connector_type")
        private String connectorType;

        @JsonProperty("connector_image")
        private String connectorImage;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("operators")
        private List<Operator> operators = new ArrayList<>();

        @JsonProperty("kamelets")
        private Kamelets kamelets = new Kamelets();

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("annotations")
        private Map<String, String> annotations = new TreeMap<>();

        @JsonProperty("consumes")
        private String consumes;
        @JsonProperty("consumes_class")
        private String consumesClass;

        @JsonProperty("produces")
        private String produces;
        @JsonProperty("produces_class")
        private String producesClass;

        @JsonProperty("error_handler_strategy")
        private String errorHandlerStrategy;

        public String getConnectorRevision() {
            return connectorRevision;
        }

        public void setConnectorRevision(String connectorRevision) {
            this.connectorRevision = connectorRevision;
        }

        public String getConnectorType() {
            return connectorType;
        }

        public void setConnectorType(String connectorType) {
            this.connectorType = connectorType;
        }

        public String getConnectorImage() {
            return connectorImage;
        }

        public void setConnectorImage(String connectorImage) {
            this.connectorImage = connectorImage;
        }

        public List<Operator> getOperators() {
            return operators;
        }

        public void setOperators(List<Operator> operators) {
            this.operators = operators;
        }

        public Kamelets getKamelets() {
            return kamelets;
        }

        public void setKamelets(Kamelets kamelets) {
            this.kamelets = kamelets;
        }

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(Map<String, String> annotations) {
            this.annotations = annotations;
        }

        public String getConsumes() {
            return consumes;
        }

        public void setConsumes(String consumes) {
            this.consumes = consumes;
        }

        public String getProduces() {
            return produces;
        }

        public void setProduces(String produces) {
            this.produces = produces;
        }

        public String getConsumesClass() {
            return consumesClass;
        }

        public void setConsumesClass(String consumesClass) {
            this.consumesClass = consumesClass;
        }

        public String getProducesClass() {
            return producesClass;
        }

        public void setProducesClass(String producesClass) {
            this.producesClass = producesClass;
        }

        public String getErrorHandlerStrategy() {
            return errorHandlerStrategy;
        }

        public void setErrorHandlerStrategy(String errorHandlerStrategy) {
            this.errorHandlerStrategy = errorHandlerStrategy;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Operator {
        @JsonProperty("type")
        private String type;

        @JsonProperty("version")
        private String version;

        public Operator() {
        }

        public Operator(String type, String version) {
            this.type = type;
            this.version = version;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Kamelets {
        @JsonProperty("adapter")
        private Kamelet adapter = new Kamelet();

        @JsonProperty("kafka")
        private Kamelet kafka = new Kamelet();

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("processors")
        private Map<String, String> processors = new HashMap<>();

        public Kamelet getAdapter() {
            return adapter;
        }

        public void setAdapter(Kamelet adapter) {
            this.adapter = adapter;
        }

        public Kamelet getKafka() {
            return kafka;
        }

        public void setKafka(Kamelet kafka) {
            this.kafka = kafka;
        }

        public Map<String, String> getProcessors() {
            return processors;
        }

        public void setProcessors(Map<String, String> processors) {
            this.processors = processors;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Kamelet {
        @JsonProperty("name")
        private String name;

        @JsonProperty("prefix")
        private String prefix;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }
}
