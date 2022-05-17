
def requiredProp = schema.requiredAt('/required')

requiredProp.add('aws_access_key')
log.info("Added aws_access_key to required properties")
requiredProp.add('aws_secret_key')
log.info("Added aws_secret_key to required properties")