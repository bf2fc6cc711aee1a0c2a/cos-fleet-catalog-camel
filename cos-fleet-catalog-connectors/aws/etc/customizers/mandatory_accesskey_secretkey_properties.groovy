
def requiredProp = schema.requiredAt('/required')

if (!requiredProp.elements().any { e -> 'aws_access_key' == e.asText() }) {
    requiredProp.add('aws_access_key')
    log.info("Added aws_access_key to required properties")
}
if (!requiredProp.elements().any { e -> 'aws_secret_key' == e.asText() }) {
    requiredProp.add('aws_secret_key')
    log.info("Added aws_secret_key to required properties")
}