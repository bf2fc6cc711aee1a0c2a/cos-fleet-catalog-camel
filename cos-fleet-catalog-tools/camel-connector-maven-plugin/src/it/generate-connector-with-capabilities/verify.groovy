import org.bf2.cos.catalog.camel.maven.connector.it.MavenTestSupport

def kamelet(String name) {
    def stream = MavenTestSupport.loadKamelet(name)
    def answer = new groovy.yaml.YamlSlurper().parse(stream)

    return answer
}


new File(basedir, "connector_source.json").withReader {
    System.out.println(it)
    def catalog = new groovy.json.JsonSlurper().parse(it)

    assert catalog.connector_type.capabilities.contains('processors')
    assert catalog.connector_type.capabilities.contains('data_shape')
    assert catalog.connector_type.capabilities.contains('error_handler')
}