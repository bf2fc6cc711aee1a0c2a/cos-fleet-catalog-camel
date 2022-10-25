package org.bf2.cos.connector.camel.it.azure.storage.blob

import com.azure.core.http.policy.HttpLogDetailLevel
import com.azure.core.http.policy.HttpLogOptions
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.common.StorageSharedKeyCredential
import groovy.util.logging.Slf4j
import org.bf2.cos.connector.camel.it.support.KafkaConnectorSpec
import spock.lang.IgnoreIf

import java.time.Instant
import java.util.concurrent.TimeUnit

@IgnoreIf({
    !hasEnv('AZURE_BLOB_ACCOUNT_NAME'   ) ||
    !hasEnv('AZURE_BLOB_ACCESS_KEY'     ) ||
    !hasEnv('AZURE_BLOB_CONTAINER_NAME' )
})
@Slf4j
class AzureStorageBlobConnectorIT extends KafkaConnectorSpec {

    def "azure-storage-blob sink"() {
        setup:
            def topic = topic()
            def payload = topic

            def containerName = System.getenv('AZURE_BLOB_CONTAINER_NAME')
            def accountName = System.getenv('AZURE_BLOB_ACCOUNT_NAME')
            def accountKey = System.getenv('AZURE_BLOB_ACCESS_KEY')
            def client = containerClient(accountName, accountKey, containerName)

            def cnt = connectorContainer('azure_storage_blob_sink_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'azure_account_name': accountName,
                'azure_container_name': containerName,
                'azure_access_key': accountKey,
                'azure_operation': 'uploadBlockBlob'
            ])

            cnt.start()

        when:
            kafka.send(topic, payload)

        then:
            def records = kafka.poll(topic)
            records.size() == 1
            records.first().value() == payload

            BlobClient bc = null

            until(10, TimeUnit.SECONDS) {
                for (def blob : client.listBlobs()) {
                    bc = client.getBlobClient(blob.name)

                    if (bc.downloadContent().toString() == payload) {
                        return true
                    }
                }

                return false
            }
        cleanup:
            if (bc != null) {
                log.info("Deleting blob ${bc.blobName}")
                bc.deleteIfExists()
                log.info("Blob ${bc.blobName} deleted")
            }

            closeQuietly(cnt)
    }

    def "azure-storage-blob source"() {
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


            def cnt = connectorContainer('azure_storage_blob_source_0.1.json', [
                'kafka_topic' : topic,
                'kafka_bootstrap_servers': kafka.outsideBootstrapServers,
                'kafka_consumer_group': group,
                'azure_account_name': accountName,
                'azure_container_name': containerName,
                'azure_access_key': accountKey,

                // keep this set to false for tests as it would delete all
                // the blobs it finds causing other tests running in parallel
                // to fail or become flaky
                "azure_delete_after_read": "false"
            ])

            cnt.start()

        when:
            bc.upload(BinaryData.fromString(payload))

        then:
            await(10, TimeUnit.SECONDS) {
                def record = kafka.poll(group, topic).find {
                    it.value() == payload && Instant.ofEpochMilli(it.timestamp()).isAfter(start)
                }

                return record != null
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
