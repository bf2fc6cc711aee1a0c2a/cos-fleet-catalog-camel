

switch (schema.channels.stable.shard_metadata.connector_type) {
    case 'source':
        assert schema.channels.stable.shard_metadata.produces != null
        break
    case 'sink':
        assert schema.channels.stable.shard_metadata.consumes != null
        break
    default:
        throw new IllegalArgumentException("Unsupported connector type: ${schema.channels.stable.shard_metadata.connector_type}")
}