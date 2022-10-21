package org.bf2.cos.connector.camel.it.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public enum ContainerImages {
    REDPANDA("docker.io/vectorized/redpanda:v22.2.6"),
    WIREMOCK("docker.io/wiremock/wiremock:2.34.0"),
    ACTIVEMQ_ARTEMIS("quay.io/artemiscloud/activemq-artemis-broker:1.0.9"),
    CASSANDRA("cassandra:3.11"),
    MONGODB("mongo:5"),
    MARIADB("mariadb:10.3.6"),
    MYSQL("mysql:5"),
    POSTGRES("postgres:14.2"),
    SQLSERVER("mcr.microsoft.com/mssql/server:2017-CU12"),
    MINIO("quay.io/minio/minio:latest"),
    LOCALSTACK("docker.io/localstack/localstack:0.14.2"),
    GCR_PUBSUB("gcr.io/google.com/cloudsdktool/cloud-sdk:emulators");

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
