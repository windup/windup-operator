#!/bin/sh -x

# everytime you test you need to increase this bundle version number
# it only affects your test, has no other purposes
# usage `./create-catalog.sh -u myuser -v 210 -m 0.0.2`
while getopts u:v:m: flag
do
    case "${flag}" in
        u) quayuser=${OPTARG};;
        v) bundleversion=${OPTARG};;
        m) mtaoperatorversion=${OPTARG};;
    esac
done

# Create operator bundle image
podman build -f mta-operator/$mtaoperatorversion/Dockerfile -t mta-operator-bundle:$bundleversion mta-operator/$mtaoperatorversion/
podman tag mta-operator-bundle:$bundleversion quay.io/$quayuser/mta-operator-bundle:$bundleversion
podman push quay.io/$quayuser/mta-operator-bundle:$bundleversion

# Install operator-registry
# git clone https://github.com/operator-framework/operator-registry
# cd operator-registry
# make build

# Build operator catalog
../../../../../operator-registry/bin/opm index add --bundles quay.io/$quayuser/mta-operator-bundle:$bundleversion \
--tag quay.io/$quayuser/mta-operator-test-catalog:$bundleversion --container-tool podman \
--from-index quay.io/openshift-community-operators/catalog:latest 

podman push  quay.io/$quayuser/mta-operator-test-catalog:$bundleversion

sleep 20

sed "s/{user}/$quayuser/g" catalog-source.yaml | sed "s/{version}/$bundleversion/g" | kubectl apply -f -