package org.bf2.cos.connector.camel.it.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public enum ContainerImages {
    REDPANDA("docker.io/vectorized/redpanda:v22.3.11"),
    WIREMOCK("docker.io/wiremock/wiremock:2.35.0"),
    ACTIVEMQ_ARTEMIS("quay.io/artemiscloud/activemq-artemis-broker:1.0.14"),
    CASSANDRA("cassandra:3.11"),
    MONGODB("mongo:5"),
    MARIADB("mariadb:10.6"),
    MYSQL("mysql:5"),
    POSTGRES("postgres:14.6"),
    SQLSERVER("mcr.microsoft.com/mssql/server:2017-CU12"),
    MINIO("quay.io/minio/minio:latest"),
    LOCALSTACK("docker.io/localstack/localstack:1.3"),
    GCR_PUBSUB("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators"),
    AZURITE("mcr.microsoft.com/azure-storage/azurite:3.21.0"),
    ELASTICSEARCH("docker.elastic.co/elasticsearch/elasticsearch:7.9.2");

    private final DockerImageName imageName;

    ContainerImages(String fullImageName) {
        this.imageName = DockerImageName.parse(fullImageName);
    }

    public DockerImageName imageName() {
        return this.imageName;
    }

    public GenericContainer<?> container() {
        return new GenericContainer<>(this.imageName);
    }

    public <T extends GenericContainer<T>> T container(Class<T> type) {
        try {
            Constructor<T> constructor = type.getConstructor(DockerImageName.class);
            T instance = constructor.newInstance(this.imageName());

            return instance;
        } catch (NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
