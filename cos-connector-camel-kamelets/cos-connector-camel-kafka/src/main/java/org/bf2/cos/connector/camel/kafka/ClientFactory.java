package org.bf2.cos.connector.camel.kafka;

import io.apicurio.registry.serde.SerdeConfig;
import org.apache.camel.component.kafka.DefaultKafkaClientFactory;
import org.apache.camel.component.kafka.KafkaConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;

import java.util.Properties;

public class ClientFactory extends DefaultKafkaClientFactory {
    private String bootstrapUrl;
    private String registryUrl;
    private String username;
    private String password;

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

    @Override
    public Producer getProducer(Properties props) {
        enrich(props);
        return super.getProducer(props);
    }

    @Override
    public Consumer getConsumer(Properties props) {
        enrich(props);
        return super.getConsumer(props);
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
}
