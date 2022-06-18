
def props = schema.requiredAt('/properties')

props.fieldNames().each {
    def f = props.get(it)

    if (it == 'gcp_service_account_key' && !f.has('oneOf')) {
        log.info("Patch gcp_service_account_key")

        f.put('type', 'string')
        f.put('format', 'password')

        def replaced = mapper.createObjectNode()
        replaced.set('title', f.get('title'))
        replaced.set('x-group', f.remove('x-group'))

        def oneOf = replaced.withArray('oneOf')
        oneOf.add(f)

        def opaque = oneOf.addObject()
        opaque.put("description", "An opaque reference to the aws_access_key")
        opaque.put("type", "object")
        opaque.put("additionalProperties", false)

        props.set(it, replaced)

        return
    }
}