package org.bf2.cos.connector.camel.it.storage.minio;

import java.time.Duration;
import java.util.UUID;

import org.bf2.cos.connector.camel.it.support.ContainerImages;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

import io.minio.MinioClient;

public class MinioContainer extends GenericContainer<MinioContainer> {
    public static final String CONTAINER_ALIAS = "tc-minio";

    public static final int PORT = 9000;

    private static final String MINIO_ACCESS_KEY = "MINIO_ACCESS_KEY";
    private static final String MINIO_SECRET_KEY = "MINIO_SECRET_KEY";
    private static final String DEFAULT_STORAGE_DIRECTORY = "/data";
    private static final String HEALTH_ENDPOINT = "/minio/health/ready";

    private final String accessKey;
    private final String secretKey;

    public MinioContainer(Network network) {
        this(network, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public MinioContainer(Network network, String accessKey, String secretKey) {
        super(ContainerImages.MINIO.imageName());

        this.accessKey = accessKey;
        this.secretKey = secretKey;

        addExposedPort(PORT);

        withNetwork(network);
        withNetworkAliases(CONTAINER_ALIAS);

        withEnv(MINIO_ACCESS_KEY, accessKey);
        withEnv(MINIO_SECRET_KEY, secretKey);

        withCommand("server", DEFAULT_STORAGE_DIRECTORY);

        setWaitStrategy(new HttpWaitStrategy()
                .forPort(PORT)
                .forPath(HEALTH_ENDPOINT)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }

    public String getHostAddress() {
        return getContainerIpAddress() + ":" + getMappedPort(PORT);
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public MinioClient client() {
        return MinioClient.builder()
                .endpoint("http://" + getHostAddress())
                .credentials(accessKey, secretKey)
                .build();
    }
}
