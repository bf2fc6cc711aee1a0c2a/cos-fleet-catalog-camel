package org.bf2.cos.catalog.camel.maven;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Parameter;

public class Connector {
    @Parameter(defaultValue = "${project.artifactId}")
    private String name;
    @Parameter(defaultValue = "${project.title}")
    private String title;
    @Parameter(defaultValue = "${project.description}")
    private String description;
    @Parameter(defaultValue = "${project.version}")
    private String version;

    @Parameter
    private KameletRef adapter;
    @Parameter
    private KameletRef kafka;
    @Parameter
    private List<KameletRef> steps;
    @Parameter
    private Map<String, Channel> channels;

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

    public KameletRef getAdapter() {
        return adapter;
    }

    public void setAdapter(KameletRef adapter) {
        this.adapter = adapter;
    }

    public KameletRef getKafka() {
        return kafka;
    }

    public void setKafka(KameletRef kafka) {
        this.kafka = kafka;
    }

    public List<KameletRef> getSteps() {
        return steps;
    }

    public void setSteps(List<KameletRef> steps) {
        this.steps = steps;
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }

    public void setChannels(Map<String, Channel> channels) {
        this.channels = channels;
    }

    public static class Channel {
        @Parameter(required = true)
        String name;
        @Parameter(defaultValue = "${cos.connector.revision}", required = true)
        String revision;
        @Parameter(defaultValue = "${quarkus.container-image.registry}/${quarkus.container-image.group}/${quarkus.container-image.name}:${quarkus.container-image.tag}", required = true)
        String image;
        @Parameter(defaultValue = "${cos.connector.operator.type}")
        String operatorType;
        @Parameter(defaultValue = "${cos.connector.operator.version}")
        String operatorVersion;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

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

    public static class KameletRef {
        @Parameter
        String name;
        @Parameter
        String version;

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
}
