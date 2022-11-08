package org.bf2.cos.connector.camel.it

import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.TestUtils
import spock.lang.IgnoreIf

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@IgnoreIf({
    !hasEnv('GCP_FUNCTIONS_PROJECT_ID'            ) ||
    !hasEnv('GCP_FUNCTIONS_FUNCTION_NAME'         ) ||
    !hasEnv('GCP_FUNCTIONS_REGION'                ) ||
    !hasEnv('GCP_FUNCTIONS_SERVICE_ACCOUNT_KEY'   )
})
@Slf4j
class ConnectorIT extends KafkaConnectorSpec {

    private static final Duration HTTP_TIMEOUT = Duration.of(5, ChronoUnit.SECONDS)
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
                                                    .connectTimeout(HTTP_TIMEOUT)
                                                    .build();

    @Override
    def setupSpec() {
    }

    def "google functions sink"() {
        setup:
            def webhookId = createWebhookToken()
            assert webhookId

            def kafkaTopic = topic()
            def kafkaGroup = UUID.randomUUID().toString()
            def message = UUID.randomUUID().toString()
            def payload =   """
                                    {"url":"https://webhook.site/${webhookId}", "message":"${message}"}
                                    """

            def cnt = connectorContainer('google_functions_sink_0.1.json', [
                'kafka_topic' : kafkaTopic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': UUID.randomUUID().toString(),
                'gcp_project_id': System.getenv('GCP_FUNCTIONS_PROJECT_ID'),
                'gcp_function_name': System.getenv('GCP_FUNCTIONS_FUNCTION_NAME'),
                'gcp_region': System.getenv('GCP_FUNCTIONS_REGION'),
                'gcp_service_account_key': System.getenv('GCP_FUNCTIONS_SERVICE_ACCOUNT_KEY')
            ])

            cnt.start()
        when:
            kafka.send(kafkaTopic, payload)
        then:
            def records = kafka.poll(kafkaGroup, kafkaTopic)
            records.size() == 1
            records.first().value() == payload

            def queryURI = URI.create("http://webhook.site/token/${webhookId}/requests?query=${message}")
            def request = HttpRequest.newBuilder(queryURI).GET().timeout(HTTP_TIMEOUT).build()

            await(21, TimeUnit.SECONDS) {
                try {
                    def response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString())

                    def body = response.body()
                    if (response.statusCode() / 100 != 2) {
                        log.info("Bad status code when trying to check webhook. Response: {}", body)
                    }

                    return body.contains(message);
                } catch (Exception e) {
                    log.info("Exception while checking webhook.", e)
                    return false;
                }
            }
        cleanup:
            closeQuietly(cnt)
    }

    static String createWebhookToken() {
        def postURI = URI.create("http://webhook.site/token/")
        def request = HttpRequest.newBuilder(postURI)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(HTTP_TIMEOUT)
                .build()

        def body = ""
        try {
            body = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body()
            return TestUtils.SLURPER.parseText(body)["uuid"];
        } catch (Exception e) {
            log.info("Exception while creating webhook. Body: {}", body, e)
            throw e;
        }
    }

}
