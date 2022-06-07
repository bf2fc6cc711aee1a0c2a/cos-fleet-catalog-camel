package org.bf2.cos.connector.camel.serdes;

import java.util.Map;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;

public class BaseConfig extends BaseKafkaSerDeConfig {
    public BaseConfig(Map<?, ?> originals) {
        super(originals);
    }
}
