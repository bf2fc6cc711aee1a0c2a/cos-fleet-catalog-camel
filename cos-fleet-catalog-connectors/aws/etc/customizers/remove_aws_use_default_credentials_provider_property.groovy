
def props = schema.requiredAt('/properties')

if (props.remove('aws_use_default_credentials_provider') != null) {
    log.info("Remove property aws_use_default_credentials_provider")
}
