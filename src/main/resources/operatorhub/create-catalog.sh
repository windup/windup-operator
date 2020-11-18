#!/bin/sh -x

# everytime you test you need to increase this version number
# it only affects your test, has no other purposes
version=0.0.16

# Create operator bundle image
podman build -f mta-operator/0.0.1/Dockerfile -t mta-operator-bundle:$version mta-operator/0.0.1/
podman tag mta-operator-bundle:$version quay.io/windupeng/mta-operator-bundle:$version
podman push quay.io/windupeng/mta-operator-bundle:$version

# Install operator-registry
# git clone https://github.com/operator-framework/operator-registry
# cd operator-registry
# make build

# Build operator catalog
../../../../../operator-registry/bin/opm index add --bundles quay.io/windupeng/mta-operator-bundle:$version \
                --from-index quay.io/openshift-community-operators/catalog:latest \
                --tag quay.io/windupeng/mta-operator-test-catalog:$version
podman push  quay.io/windupeng/mta-operator-test-catalog:$version

sleep 20

oc apply -f catalog-source.yaml