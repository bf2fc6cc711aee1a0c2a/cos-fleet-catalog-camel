package org.bf2.cos.connector.camel.it.aws;

import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

public class AWSContainer extends LocalStackContainer {
    public static final String IMAGE = "localstack/localstack:0.14.1";
    public static final String CONTAINER_ALIAS = "tc-aws";
    public static final int PORT = 4566;

    public AWSContainer(Network network, Service... services) {
        super(DockerImageName.parse(IMAGE));

        withServices(services);
        withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CONTAINER_ALIAS)));
        withNetwork(network);
        withNetworkAliases(CONTAINER_ALIAS);
    }

    public AmazonSQS sqs() {
        return AmazonSQSClientBuilder.standard()
                .withEndpointConfiguration(getEndpointConfiguration(LocalStackContainer.Service.SQS))
                .withCredentials(getDefaultCredentialsProvider())
                .build();
    }

    public String getEndpoint() {
        return "http://" + CONTAINER_ALIAS + ":" + PORT;
    }
}
