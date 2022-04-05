import org.bf2.cos.catalog.camel.maven.connector.it.MavenTestSupport

def kamelet(String name) {
    def stream = MavenTestSupport.loadKamelet(name)
    def answer = new groovy.yaml.YamlSlurper().parse(stream)

    return answer
}


new File(basedir, "connector_source.json").withReader {
    System.out.println(it)
    def catalog = new groovy.json.JsonSlurper().parse(it)

    catalog.channels.stable.shard_metadata.with {
        assert connector_revision == '1'
        assert connector_type == 'source'
        assert connector_image == 'registry.io/org.bf2.it/generate-connector-simple:test'

        assert operators[0].type == 'camel-connector-operator'
        assert operators[0].version == '[1.0.0,2.0.0)'

        assert kamelets.adapter.name == 'test-source'
        assert kamelets.adapter.prefix == 'test'
        assert kamelets.kafka.name == 'test-kafka-sink'
        assert kamelets.kafka.prefix == 'kafka'

        assert produces == 'application/json'
        assert produces_class == 'com.acme.Foo'
        assert error_handler_strategy == 'log'
    }

    catalog.connector_type.schema.properties.with {
        data_shape.with {
            assert properties.produces != null
            assert properties.consumes == null

            assert properties.produces['$ref'] == '#/$defs/data_shape/produces'
        }
    }

    catalog.connector_type.schema['$defs'].data_shape.with {
        assert produces != null
        assert consumes == null

        produces.with {
            assert properties.format.type == 'string'
            assert properties.format.default == 'application/json'
            assert properties.format.enum.size() == 1
            assert properties.format.enum[0] == 'application/json'
        }
    }

    kamelet('test-source').with {
        for (entry in spec?.definition?.properties) {
            def k = MavenTestSupport.asKey(entry.key)

            assert catalog.connector_type.schema.properties["test_${k}"] != null
            assert catalog.connector_type.schema.properties["test_${k}"].title == entry.value.title
            assert catalog.connector_type.schema.properties["test_${k}"].description == entry.value.description
            assert catalog.connector_type.schema.properties["test_${k}"].type == entry.value.type
        }
    }

    kamelet('test-kafka-sink').with {
        for (entry in spec?.definition?.properties) {
            def k = MavenTestSupport.asKey(entry.key)

            assert catalog.connector_type.schema.properties["kafka_${k}"] != null
            assert catalog.connector_type.schema.properties["kafka_${k}"].title == entry.value.title
            assert catalog.connector_type.schema.properties["kafka_${k}"].description == entry.value.description
            assert catalog.connector_type.schema.properties["kafka_${k}"].type == entry.value.type
        }
    }
}

new File(basedir, "connector_sink.json").withReader {
    def catalog = new groovy.json.JsonSlurper().parse(it)

    catalog.channels.stable.shard_metadata.with {
        assert connector_revision == '1'
        assert connector_type == 'sink'
        assert connector_image == 'registry.io/org.bf2.it/generate-connector-simple:test'

        assert operators[0].type == 'camel-connector-operator'
        assert operators[0].version == '[1.0.0,2.0.0)'

        assert kamelets.adapter.name == 'test-sink'
        assert kamelets.adapter.prefix == 'test'
        assert kamelets.kafka.name == 'test-kafka-source'
        assert kamelets.kafka.prefix == 'kafka'

        assert consumes == 'application/xml'
        assert consumes_class == 'com.acme.Bar'
        assert error_handler_strategy == 'stop'
    }

    catalog.connector_type.schema.properties.with {
        data_shape.with {
            assert properties.consumes != null
            assert properties.produces == null

            assert properties.consumes['$ref'] == '#/$defs/data_shape/consumes'
        }
    }

    catalog.connector_type.schema['$defs'].data_shape.with {
        assert produces == null
        assert consumes != null

        consumes.with {
            assert properties.format.type == 'string'
            assert properties.format.default == 'application/xml'
            assert properties.format.enum.size() == 1
            assert properties.format.enum[0] == 'application/xml'
        }
    }

    kamelet('test-sink').with {
        for (entry in spec?.definition?.properties) {
            def k = MavenTestSupport.asKey(entry.key)

            assert catalog.connector_type.schema.properties["test_${k}"] != null
            assert catalog.connector_type.schema.properties["test_${k}"].title == entry.value.title
            assert catalog.connector_type.schema.properties["test_${k}"].description == entry.value.description
            assert catalog.connector_type.schema.properties["test_${k}"].type == entry.value.type
        }
    }

    kamelet('test-kafka-source').with {
        for (entry in spec?.definition?.properties) {
            def k = MavenTestSupport.asKey(entry.key)

            assert catalog.connector_type.schema.properties["kafka_${k}"] != null
            assert catalog.connector_type.schema.properties["kafka_${k}"].title == entry.value.title
            assert catalog.connector_type.schema.properties["kafka_${k}"].description == entry.value.description
            assert catalog.connector_type.schema.properties["kafka_${k}"].type == entry.value.type
        }
    }
}
