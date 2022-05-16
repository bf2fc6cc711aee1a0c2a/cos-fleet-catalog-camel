
def props = schema.requiredAt('/properties')

props.fieldNames().each {
    def f = props.get(it)
    def t = f.get('type')

    if (t != null && t.asText() == 'binary') {
        log.info("Patch binary property ${it}")

        props.with(it).put('type', 'string')
        props.with(it).put('format', 'base64')
    }
}