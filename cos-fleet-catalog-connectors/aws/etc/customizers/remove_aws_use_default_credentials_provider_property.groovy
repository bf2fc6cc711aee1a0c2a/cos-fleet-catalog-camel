
def props = schema.requiredAt('/properties')

props.remove('aws_use_default_credentials_provider')
log.info("Remove property aws_use_default_credentials_provider")
