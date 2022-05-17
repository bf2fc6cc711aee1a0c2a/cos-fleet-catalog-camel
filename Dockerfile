FROM registry.access.redhat.com/ubi8/ubi-minimal:8.5

COPY ./etc/kubernetes/manifests/base/connectors /etc/connectors