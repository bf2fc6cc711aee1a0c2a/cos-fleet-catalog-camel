package org.bf2.cos.connector.camel.it.support

class ContainerFile {
    String path
    String body

    ContainerFile(String path, String body) {
        this.path = path
        this.body = body
    }

    static ContainerFile of(String path, String body) {
        return new ContainerFile(path: path, body: body)
    }
}
