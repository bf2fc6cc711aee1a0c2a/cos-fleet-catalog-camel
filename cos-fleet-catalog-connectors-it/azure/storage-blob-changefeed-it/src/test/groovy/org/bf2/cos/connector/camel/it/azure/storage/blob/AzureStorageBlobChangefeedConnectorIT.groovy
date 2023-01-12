package org.bf2.cos.connector.camel.it.azure.storage.blob

import com.azure.core.http.policy.HttpLogDetailLevel
import com.azure.core.http.policy.HttpLogOptions
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import org.bf2.cos.connector.camel.it.support.spock.RequiresDefinition
import spock.lang.IgnoreIf

import java.time.Instant
import java.util.concurrent.TimeUnit

@IgnoreIf({
    !hasEnv('AZURE_BLOB_ACCOUNT_NAME'   ) ||
    !hasEnv('AZURE_BLOB_ACCESS_KEY'     ) ||
    !hasEnv('AZURE_BLOB_CONTAINER_NAME' )
})
@Slf4j
@RequiresDefinition('azure_storage_blob_changefeed_source_0.1.json')
class AzureStorageBlobChangefeedConnectorIT extends KafkaConnectorSpec {

    def "azure-storage-blob-changefeed source"() {
        setup:
            def topic = topic()
            def group = UUID.randomUUID().toString()
            def payload = topic

            def containerName = System.getenv('AZURE_BLOB_CONTAINER_NAME')
            def accountName = System.getenv('AZURE_BLOB_ACCOUNT_NAME')
            def accountKey = System.getenv('AZURE_BLOB_ACCESS_KEY')
            def client = containerClient(accountName, accountKey, containerName)
            def bc = client.getBlobClient(topic)
            def start = Instant.now()

            def cnt = connectorContainer('azure_storage_blob_changefeed_source_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': group,
                'azure_period': '5000',
                'azure_account_name': accountName,
                'azure_access_key': accountKey
            ])

            cnt.start()

        when:
            bc.upload(BinaryData.fromString(payload))

        then:
            //XXX: unfortunately events take a while to be available in the changefeed
            await(300, TimeUnit.SECONDS) {
                    try {
                        kafka.poll(group, topic).any {
                            log.info("RECORD = {} is {}", topic, new JsonSlurper().parseText(it.value()).blobUrl)
                            return it.value() != null
                                    && it.value().contains(topic)
                                    && Instant.ofEpochMilli(it.timestamp()).isAfter(start)
                                    && !it.headers().headers("azure-storage-blob-changefeed-eventTime").isEmpty()
                                    && !it.headers().headers("azure-storage-blob-changefeed-eventType").isEmpty()
                                    && !it.headers().headers("azure-storage-blob-changefeed-id").isEmpty()
                                    && !it.headers().headers("azure-storage-blob-changefeed-subject").isEmpty()
                                    && !it.headers().headers("azure-storage-blob-changefeed-topic").isEmpty()
                        }
                    } catch (Exception e ) {
                        return false
                    }
            }

        cleanup:
            if (bc != null) {
                log.info("Deleting blob ${topic}")
                bc.deleteIfExists()
                log.info("Blob ${topic} deleted")
            }

            closeQuietly(cnt)
    }

    static BlobContainerClient containerClient(String accountName, String accountKey, String containerName) {
        HttpLogOptions httpOptions = new HttpLogOptions()
                .setLogLevel(HttpLogDetailLevel.BODY_AND_HEADERS)
                .setPrettyPrintBody(true)

        def bsc = new BlobServiceClientBuilder()
                .endpoint("https://${accountName}.blob.core.windows.net/")
                .httpLogOptions(httpOptions)
                .credential(new StorageSharedKeyCredential(accountName, accountKey))
                .buildClient()

        return bsc.getBlobContainerClient(containerName)
    }
}
