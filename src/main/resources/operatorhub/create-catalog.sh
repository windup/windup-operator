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
sed -i "s/quay.io\/windupeng/quay.io\/$quayuser/g" "mta-operator/$mtaoperatorversion/manifests/windup-operator.v0.0.2.clusterserviceversion.yaml"
podman build -f mta-operator/$mtaoperatorversion/Dockerfile -t mta-operator-bundle:$bundleversion mta-operator/$mtaoperatorversion/
podman tag mta-operator-bundle:$bundleversion quay.io/$quayuser/mta-operator-bundle:$bundleversion
podman push quay.io/$quayuser/mta-operator-bundle:$bundleversion
sed -i "s/quay.io\/$quayuser/quay.io\/windupeng/g" "mta-operator/$mtaoperatorversion/manifests/windup-operator.v0.0.2.clusterserviceversion.yaml"


# Install operator-registry
# git clone https://github.com/operator-framework/operator-registry
# cd operator-registry
# make build

# Build operator catalog
# if version on argument is 0.0.1 we will not create the catalog from the community one as it already has that version and would crash
if [ "0.0.1" = "$mtaoperatorversion" ]; then
../../../../../operator-registry/bin/opm index add --bundles quay.io/$quayuser/mta-operator-bundle:$bundleversion \
--tag quay.io/$quayuser/mta-operator-test-catalog:$bundleversion --container-tool podman
else
../../../../../operator-registry/bin/opm index add --bundles quay.io/$quayuser/mta-operator-bundle:$bundleversion \
--tag quay.io/$quayuser/mta-operator-test-catalog:$bundleversion --container-tool podman \
--from-index quay.io/openshift-community-operators/catalog:latest 
fi

podman push  quay.io/$quayuser/mta-operator-test-catalog:$bundleversion

sleep 20

kubectl delete catalogsource test-catalog -n openshift-marketplace
sed "s/{user}/$quayuser/g" catalog-source.yaml | sed "s/{version}/$bundleversion/g" | kubectl create -f -