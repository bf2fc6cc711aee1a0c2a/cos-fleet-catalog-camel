package org.bf2.cos.catalog.camel.maven.connector.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class Connector {
    @Param(defaultValue = "${${cos.connector.type}-${cos.connector.version}")
    private String name;
    @Param(defaultValue = "${project.title}")
    private String title;
    @Param(defaultValue = "${project.description}")
    private String description;
    @Param(defaultValue = "${cos.connector.version}")
    private String version;

    @Param
    private EndpointRef adapter;
    @Param
    private EndpointRef kafka;
    @Param
    private List<ActionRef> actions;
    @Param
    private Map<String, Channel> channels;
    @Param
    private DataShapeDefinition dataShape;
    @Param
    private ErrorHandler errorHandler;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public EndpointRef getAdapter() {
        return adapter;
    }

    public void setAdapter(EndpointRef adapter) {
        this.adapter = adapter;
    }

    public EndpointRef getKafka() {
        return kafka;
    }

    public void setKafka(EndpointRef kafka) {
        this.kafka = kafka;
    }

    public List<ActionRef> getActions() {
        return actions;
    }

    public void setActions(List<ActionRef> actions) {
        this.actions = actions;
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }

    public void setChannels(Map<String, Channel> channels) {
        this.channels = channels;
    }

    public DataShapeDefinition getDataShape() {
        return dataShape;
    }

    public void setDataShape(DataShapeDefinition dataShape) {
        this.dataShape = dataShape;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public static class Channel {
        @Param(defaultValue = "${cos.connector.revision}", required = true)
        String revision;
        @Param(defaultValue = "${quarkus.container-image.registry}/${quarkus.container-image.group}/${quarkus.container-image.name}:${quarkus.container-image.tag}", required = true)
        String image;
        @Param(defaultValue = "${cos.connector.operator.type}")
        String operatorType;
        @Param(defaultValue = "${cos.connector.operator.version}")
        String operatorVersion;

        public String getRevision() {
            return revision;
        }

        public void setRevision(String revision) {
            this.revision = revision;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }

        public String getOperatorType() {
            return operatorType;
        }

        public void setOperatorType(String operatorType) {
            this.operatorType = operatorType;
        }

        public String getOperatorVersion() {
            return operatorVersion;
        }

        public void setOperatorVersion(String operatorVersion) {
            this.operatorVersion = operatorVersion;
        }
    }

    public static class EndpointRef {
        @Param
        String prefix;
        @Param
        String name;
        @Param
        String version;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    public static class ActionRef {
        @Param
        String name;
        @Param
        String version;
        @Param
        Map<String, String> metadata;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }

    public static class DataShapeDefinition {
        @Param
        DataShape consumes;
        @Param
        DataShape produces;

        public DataShape getConsumes() {
            return consumes;
        }

        public void setConsumes(DataShape consumes) {
            this.consumes = consumes;
        }

        public DataShape getProduces() {
            return produces;
        }

        public void setProduces(DataShape produces) {
            this.produces = produces;
        }
    }

    public static DataShape dataShape() {
        return new DataShape();
    }

    public static DataShape dataShape(String defaultFormat, Collection<String> formats) {
        return new DataShape(defaultFormat, new TreeSet<>(formats));
    }

    public static DataShape dataShape(String defaultFormat, Collection<String> formats,
            DataShape.SchemaStrategy schemaStrategy) {
        return new DataShape(defaultFormat, new TreeSet<>(formats), schemaStrategy);
    }

    public static class DataShape {
        public enum SchemaStrategy {
            NONE,
            OPTIONAL,
            REQUIRED
        }

        @Param
        String defaultFormat;
        @Param
        Set<String> formats;
        @Param
        SchemaStrategy schemaStrategy;
        @Param
        String contentClass;

        public DataShape() {
            this(null, null, SchemaStrategy.NONE);
        }

        public DataShape(String defaultFormat, Set<String> formats) {
            this(defaultFormat, formats, SchemaStrategy.NONE);
        }

        public DataShape(String defaultFormat, Set<String> formats, SchemaStrategy schemaStrategy) {
            this.defaultFormat = defaultFormat;
            this.formats = formats;
            this.schemaStrategy = schemaStrategy;
        }

        public String getDefaultFormat() {
            return defaultFormat;
        }

        public void setDefaultFormat(String defaultFormat) {
            this.defaultFormat = defaultFormat;
        }

        public Set<String> getFormats() {
            return formats;
        }

        public void setFormats(Set<String> formats) {
            this.formats = formats;
        }

        public SchemaStrategy getSchemaStrategy() {
            return schemaStrategy;
        }

        public void setSchemaStrategy(SchemaStrategy schemaStrategy) {
            this.schemaStrategy = schemaStrategy;
        }

        public String getContentClass() {
            return contentClass;
        }

        public void setContentClass(String contentClass) {
            this.contentClass = contentClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DataShape)) {
                return false;
            }
            DataShape shape = (DataShape) o;

            return Objects.equals(getDefaultFormat(), shape.getDefaultFormat())
                    && Objects.equals(getFormats(), shape.getFormats())
                    && getSchemaStrategy() == shape.getSchemaStrategy()
                    && Objects.equals(getContentClass(), shape.getContentClass());
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    getDefaultFormat(),
                    getFormats(),
                    getSchemaStrategy(),
                    getContentClass());
        }

        @Override
        public String toString() {
            return "DataShape{" +
                    "defaultFormat='" + defaultFormat + '\'' +
                    ", formats=" + formats +
                    ", schemaStrategy=" + schemaStrategy +
                    ", contentClass=" + contentClass +
                    '}';
        }
    }

    public static class ErrorHandler {
        public enum Strategy {
            LOG,
            STOP,
            DEAD_LETTER_QUEUE
        }

        @Param
        Strategy defaultStrategy;
        @Param
        Set<Strategy> strategies;

        public Strategy getDefaultStrategy() {
            return defaultStrategy;
        }

        public void setDefaultStrategy(Strategy defaultStrategy) {
            this.defaultStrategy = defaultStrategy;
        }

        public Set<Strategy> getStrategies() {
            return strategies;
        }

        public void setStrategies(Set<Strategy> strategies) {
            this.strategies = strategies;
        }
    }
}
