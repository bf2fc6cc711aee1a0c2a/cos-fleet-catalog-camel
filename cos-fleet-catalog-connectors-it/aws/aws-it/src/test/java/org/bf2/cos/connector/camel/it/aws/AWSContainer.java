package org.bf2.cos.connector.camel.it.aws;

import java.net.URI;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

public class AWSContainer extends GenericContainer<AWSContainer> {
    public static final String IMAGE = "localstack/localstack:0.14.2";
    public static final String CONTAINER_ALIAS = "tc-aws";
    public static final int PORT = 4566;

    public AWSContainer(Network network, String... services) {
        super(DockerImageName.parse(IMAGE));

        withEnv("SERVICE", String.join(",", services));
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CONTAINER_ALIAS)));
        withNetwork(network);
        withNetworkAliases(CONTAINER_ALIAS);
        withExposedPorts(PORT);
        waitingFor(Wait.forLogMessage(".*Ready\\.\n", 1));
    }

    public SqsClient sqs() {
        return SqsClient.builder()
                .credentialsProvider(getCredentialsProvider())
                .endpointOverride(getExternalEndpointURI())
                .region(Region.US_EAST_1)
                .build();
    }

    public S3Client s3() {
        return S3Client.builder()
                .credentialsProvider(getCredentialsProvider())
                .endpointOverride(getExternalEndpointURI())
                .region(Region.US_EAST_1)
                .build();
    }

    public SnsClient sns() {
        return SnsClient.builder()
                .credentialsProvider(getCredentialsProvider())
                .endpointOverride(getExternalEndpointURI())
                .region(Region.US_EAST_1)
                .build();
    }

    public KinesisClient kinesis() {
        return KinesisClient.builder()
                .credentialsProvider(getCredentialsProvider())
                .endpointOverride(getExternalEndpointURI())
                .region(Region.US_EAST_1)
                .build();
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return this::getCredentials;
    }

    public AwsCredentials getCredentials() {
        return new AwsCredentials() {
            @Override
            public String accessKeyId() {
                return "accesskey";
            }

            @Override
            public String secretAccessKey() {
                return "secretkey";
            }
        };
    }

    public String getRegion() {
        return Region.US_EAST_1.id();
    }

    public String getEndpoint() {
        return "http://" + CONTAINER_ALIAS + ":" + PORT;
    }

    public URI getEndpointURI() {
        return URI.create(getEndpoint());
    }

    public String getExternalEndpoint() {
        return "http://" + getContainerIpAddress() + ":" + getMappedPort(PORT);
    }

    public URI getExternalEndpointURI() {
        return URI.create(getExternalEndpoint());
    }
}
