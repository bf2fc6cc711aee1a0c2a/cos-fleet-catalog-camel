package org.bf2.cos.connector.camel.kafka;

import io.apicurio.registry.serde.SerdeConfig;
import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.DefaultKafkaClientFactory;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.spi.CDI;
import java.util.Properties;

public class ClientFactory extends DefaultKafkaClientFactory {

    private static final Logger LOG = LoggerFactory.getLogger(ClientFactory.class);

    private String bootstrapUrl;
    private String registryUrl;
    private String username;
    private String password;
    private int consumerCreationRetryMs;
    private int producerCreationRetryMs;

    public String getBootstrapUrl() {
        return bootstrapUrl;
    }

    public void setBootstrapUrl(String bootstrapUrl) {
        this.bootstrapUrl = bootstrapUrl;
    }

    public String getRegistryUrl() {
        return registryUrl;
    }

    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getConsumerCreationRetryMs() {
        return consumerCreationRetryMs;
    }

    public void setConsumerCreationRetryMs(int consumerCreationRetryMs) {
        this.consumerCreationRetryMs = consumerCreationRetryMs;
    }

    public int getProducerCreationRetryMs() {
        return producerCreationRetryMs;
    }

    public void setProducerCreationRetryMs(int producerCreationRetryMs) {
        this.producerCreationRetryMs = producerCreationRetryMs;
    }

    @Override
    public Producer getProducer(Properties props) {
        enrich(props);
        KafkaProducer producer = null;
        try {
            return producer = (KafkaProducer) super.getProducer(props);
        } catch (KafkaException ke) {
            int retryMs = getProducerCreationRetryMs();
            LOG.warn("KafkaException when trying to create producer. Will wait {}ms before retry.", retryMs);
            sleep(retryMs);
            throw ke;
        } finally {
            KafkaHealthCheckRepository.get(getCamelContext())
                    .addHealthCheck(
                            new KafkaProducersHealthCheck(bootstrapUrl, producer, props));
        }
    }

    @Override
    public Consumer getConsumer(Properties props) {
        enrich(props);
        KafkaConsumer consumer = null;
        try {
            return consumer = (KafkaConsumer) super.getConsumer(props);
        } catch (KafkaException ke) {
            int retryMs = getConsumerCreationRetryMs();
            LOG.warn("KafkaException when trying to create consumer. Will wait {}ms before retry.", retryMs);
            sleep(retryMs);
            throw ke;
        } finally {
            KafkaHealthCheckRepository.get(getCamelContext())
                    .addHealthCheck(
                            new KafkaConsumersHealthCheck(bootstrapUrl, consumer, props));
        }
    }

    @Override
    public String getBrokers(KafkaConfiguration configuration) {
        return this.bootstrapUrl;
    }

    private void enrich(Properties props) {
        //
        // Configure Apicurio registry
        //
        if (ObjectHelper.isNotEmpty(registryUrl)) {
            props.put(SerdeConfig.REGISTRY_URL, registryUrl);
            props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);

            if (ObjectHelper.isNotEmpty(username)) {
                props.put(SerdeConfig.AUTH_USERNAME, username);
            }
            if (ObjectHelper.isNotEmpty(password)) {
                props.put(SerdeConfig.AUTH_PASSWORD, password);
            }
        }
    }

    private CamelContext getCamelContext() {
        return CDI.current().select(CamelContext.class).get();
    }

    private void sleep(int retryMs) {
        try {
            Thread.sleep(retryMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Sleep interrupted");
        }
    }

}
