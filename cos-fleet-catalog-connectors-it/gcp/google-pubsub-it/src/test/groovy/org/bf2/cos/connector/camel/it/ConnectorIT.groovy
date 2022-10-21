package org.bf2.cos.connector.camel.it

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.gax.core.NoCredentialsProvider
import com.google.api.gax.grpc.GrpcTransportChannel
import com.google.api.gax.rpc.FixedTransportChannelProvider
import com.google.api.gax.rpc.TransportChannelProvider
import com.google.cloud.pubsub.v1.SubscriptionAdminClient
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings
import com.google.cloud.pubsub.v1.TopicAdminClient
import com.google.cloud.pubsub.v1.TopicAdminSettings
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub
import com.google.cloud.pubsub.v1.stub.SubscriberStub
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.PullRequest
import com.google.pubsub.v1.PullResponse
import com.google.pubsub.v1.Subscription
import com.google.pubsub.v1.Topic
import com.google.pubsub.v1.TopicName
import groovy.util.logging.Slf4j
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.testcontainers.containers.PubSubEmulatorContainer

import java.util.concurrent.TimeUnit

@Slf4j
class ConnectorIT extends KafkaConnectorSpec {
    static PubSubEmulatorContainer container
    static final String PROJECT_ID = "toys"

    @Override
    def setupSpec() {
        container = ContainerImages.GCR_PUBSUB.container(PubSubEmulatorContainer.class)
        container.withLogConsumer(logger('tc-google-pubsub'))
        container.withNetwork(network)
        container.withNetworkAliases('tc-google-pubsub')
        container.start()
    }

    def "google pubsub sink"() {
        setup:
            def kafkaTopic = topic()
            def kafkaGroup = UUID.randomUUID().toString()
            def payload = '''{ "value": "4", "suit": "hearts" }'''

            ManagedChannel channel = ManagedChannelBuilder.forTarget(container.getEmulatorEndpoint()).usePlaintext().build();
            TransportChannelProvider channelProvider = FixedTransportChannelProvider.create(
                    GrpcTransportChannel.create(channel)
            );

            NoCredentialsProvider credentialsProvider = NoCredentialsProvider.create();
            String topicId = "cards"
            createTopic(topicId, channelProvider, credentialsProvider);

            String subscriptionId = topicId + "-subscription"
            createSubscription(subscriptionId, topicId, channelProvider, credentialsProvider);
            SubscriberStubSettings subscriberStubSettings = SubscriberStubSettings
                    .newBuilder()
                    .setTransportChannelProvider(channelProvider)
                    .setCredentialsProvider(credentialsProvider)
                    .build();

            def cnt = connectorContainer('google_pubsub_sink_0.1.json', [
                'kafka_topic' : kafkaTopic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'gcp_project_id': PROJECT_ID,
                'gcp_destination_name': topicId,
                'gcp_service_account_key': ""
            ])
            cnt.withEnv("camel.component.google-pubsub.authenticate", "false")
            cnt.withEnv("camel.component.google-pubsub.endpoint", "tc-google-pubsub:8085")

            cnt.withEnv("quarkus.log.level", "DEBUG")
            cnt.withEnv("quarkus.log.category.\"org.apache.camel.component\".level", "DEBUG")

            cnt.start()
        when:
            kafka.send(kafkaTopic, payload)
        then:
            def records = kafka.poll(kafkaGroup, kafkaTopic)
            records.size() == 1
            records.first().value() == payload

            SubscriberStub subscriber = GrpcSubscriberStub.create(subscriberStubSettings)
            PullRequest pullRequest = PullRequest
                    .newBuilder()
                    .setMaxMessages(1)
                    .setReturnImmediately(true)
                    .setSubscription(ProjectSubscriptionName.format(PROJECT_ID, subscriptionId))
                    .build();

            await(10, TimeUnit.SECONDS) {
                PullResponse pullResponse = subscriber.pullCallable().call(pullRequest);
                println(pullResponse.getReceivedMessagesList())

                if (pullResponse.getReceivedMessagesList().size() == 1) {
                    def response = pullResponse.getReceivedMessagesList().get(0).getMessage().getData().toByteArray()

                    def mapper = new ObjectMapper()
                    def actual = mapper.readTree(response)
                    def expected = mapper.readTree(payload)

                    return actual == expected
                }
                return false
            }

        cleanup:
            closeQuietly(cnt)
            channel.shutdown()
            closeQuietly(subscriber)
            closeQuietly(container)
    }

    private void createTopic(String topicId,
                             TransportChannelProvider channelProvider,
                             NoCredentialsProvider credentialsProvider
    ) throws IOException {
        TopicAdminSettings topicAdminSettings = TopicAdminSettings
                .newBuilder()
                .setTransportChannelProvider(channelProvider)
                .setCredentialsProvider(credentialsProvider)
                .build();
        try (TopicAdminClient topicAdminClient = TopicAdminClient.create(topicAdminSettings)) {
            TopicName topicName = TopicName.of(PROJECT_ID, topicId);
            topicAdminClient.createTopic(topicName);
        }
    }

    private void createSubscription(
            String subscriptionId,
            String topicId,
            TransportChannelProvider channelProvider,
            NoCredentialsProvider credentialsProvider
    ) throws IOException {
        SubscriptionAdminSettings subscriptionAdminSettings = SubscriptionAdminSettings
                .newBuilder()
                .setTransportChannelProvider(channelProvider)
                .setCredentialsProvider(credentialsProvider)
                .build();

        Topic topic = Topic.newBuilder().setName(TopicName.format(PROJECT_ID, topicId)).build();
        Subscription subscription = Subscription.newBuilder()
                .setName(ProjectSubscriptionName.format(PROJECT_ID, subscriptionId))
                .setTopic(topic.getName())
                .setAckDeadlineSeconds(10)
                .build();

        SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(subscriptionAdminSettings);
        subscriptionAdminClient.createSubscription(subscription);
    }

}
