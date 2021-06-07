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

    public static class Channel {
        @Parameter
        String revision;
        @Parameter
        String image;
        @Parameter
        Operator operator;

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

        public Operator getOperator() {
            return operator;
        }

        public void setOperator(Operator operator) {
            this.operator = operator;
        }
    }

    public static class Operator {
        @Parameter(defaultValue = "${cos.connector.operator.type}")
        String type;
        @Parameter
        String version;

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
}
