package org.bf2.cos.connector.camel.it.elasticsearch

import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.bf2.cos.connector.camel.it.support.AwaitStrategy
import org.bf2.cos.connector.camel.it.support.ConnectorSpecSupport
import org.bf2.cos.connector.camel.it.support.ContainerImages
import org.elasticsearch.client.Request
import org.elasticsearch.client.RestClient
import org.testcontainers.containers.ContainerState
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.WaitStrategy
import org.testcontainers.elasticsearch.ElasticsearchContainer

class ElasticsearchSupport {
    static final int ELASTIC_PORT = 9200

    static RestClient client(ContainerState container, String user, String password) {
        def host = HttpHost.create(container.host + ':' + container.getMappedPort(ELASTIC_PORT))
        def builder = RestClient.builder(host)

        if (user != null && password != null) {
            def cp = new BasicCredentialsProvider();
            cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(user, password))

            builder.setHttpClientConfigCallback(hcb -> {
                return hcb.setDefaultCredentialsProvider(cp)
            })
        }

        return builder.build()
    }

    static WaitStrategy waitForConnection(String user, String password) {
        def hc = new Request("GET", "/_cluster/health")

        return new AwaitStrategy() {
            @Override
            boolean ready() {
                try (RestClient c = client(target, user, password)) {
                    def r = c.performRequest(hc)
                    return r.statusLine.statusCode >= 200 && r.statusLine.statusCode < 300
                } catch (Exception e) {
                    return false
                }
            }
        }
    }

    static ElasticsearchContainer elasticsearchContainer(Network network, String alias, String user, String password) {
        def elastic = ContainerImages.ELASTICSEARCH.container(ElasticsearchContainer.class)
        elastic.withLogConsumer(ConnectorSpecSupport.logger(alias))
        elastic.withNetwork(network)
        elastic.withNetworkAliases(alias)
        elastic.withExposedPorts(ElasticsearchSupport.ELASTIC_PORT)
        elastic.waitingFor(waitForConnection(user, password))

        if (user != null && password != null) {
            elastic.withPassword(password)
        }

        return elastic
    }
}
