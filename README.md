# cos-fleet-catalog-camel

## Generate resources

Running `./gen_manifests.sh` will generate OpenShift template and Kustomize file from `etc/kubernetes/manifests/base/connectors`:
- `templates/cos-fleet-catalog-camel.yaml`
- `etc/kubernetes/manifests/base/kustomization.yaml`

Setting `BUILD=true` will regenerate the content in `etc/kubernetes/manifests/base/connectors`

```bash
BUILD=true ./gen_manifests.sh
```
