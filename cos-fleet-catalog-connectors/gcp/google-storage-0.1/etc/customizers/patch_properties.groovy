

def key = schema.requiredAt('/properties/gcp_service_account_key')

if  (key.get('type').asText() == 'binary') {
    key.put('type', 'string')
    key.put('format', 'base64')
}