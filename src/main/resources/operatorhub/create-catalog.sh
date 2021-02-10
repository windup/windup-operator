#!/bin/sh -x

# everytime you test you need to increase this bundle version number
# it only affects your test, has no other purposes
# usage `./create-catalog.sh -u myuser -v 210 -m 0.0.2 -b 1`
# -u : quay user
# -v : version of the catalog
# -m : version of the MTA Operator
# -b : do the operator build first ? 1 yes , 0 no

while getopts u:v:m:b: flag
do
    case "${flag}" in
        u) quayuser=${OPTARG};;
        v) bundleversion=${OPTARG};;
        m) mtaoperatorversion=${OPTARG};;
        b) dobuild=${OPTARG};;
    esac
done

# Do the build of the operator with current code, if requested
if [ "1" = "$dobuild" ]; then 
  cd ../../../..
  ./mvnw clean package -Pnative -DskipTests \
            -Dquarkus.native.container-build=true \
            -Dquarkus.native.container-runtime=podman \
            -Dquarkus.container-image.build=true  \
            -Dquarkus.container-image.tag=$mtaoperatorversion \
            -Dquarkus.container-image.push=true \
            -Dquarkus.container-image.group=$quayuser
  cd src/main/resources/operatorhub
fi

# Install operator-registry
# git clone https://github.com/operator-framework/operator-registry
# cd operator-registry
# make build

# Build operator catalog
# if version on argument is the latest version , not published yet, it will create the catalog from the community one to include past published versions
# it will also mean the container image for the operator will be the one on the $user quay account
if [ "0.0.2" == "$mtaoperatorversion" ]; then
# Create operator bundle image using user quay image for operator
sed -i "s/quay.io\/windupeng/quay.io\/$quayuser/g" "mta-operator/$mtaoperatorversion/manifests/windup-operator.v$mtaoperatorversion.clusterserviceversion.yaml"
podman build -f mta-operator/$mtaoperatorversion/Dockerfile -t mta-operator-bundle:$bundleversion mta-operator/$mtaoperatorversion/
podman tag mta-operator-bundle:$bundleversion quay.io/$quayuser/mta-operator-bundle:$bundleversion
podman push quay.io/$quayuser/mta-operator-bundle:$bundleversion
sed -i "s/quay.io\/$quayuser/quay.io\/windupeng/g" "mta-operator/$mtaoperatorversion/manifests/windup-operator.v$mtaoperatorversion.clusterserviceversion.yaml"

../../../../../operator-registry/bin/opm index add --bundles quay.io/$quayuser/mta-operator-bundle:$bundleversion \
--tag quay.io/$quayuser/mta-operator-test-catalog:$bundleversion --container-tool podman \
--from-index quay.io/openshift-community-operators/catalog:latest 
else 
# Using an already published version means we are not going to include the community-operators catalog as that one already has this version
# Create operator test bundle image with the windupeng 0.0.1 operator image
podman build -f mta-operator/$mtaoperatorversion/Dockerfile -t mta-operator-bundle:$bundleversion mta-operator/$mtaoperatorversion/
podman tag mta-operator-bundle:$bundleversion quay.io/$quayuser/mta-operator-bundle:$bundleversion
podman push quay.io/$quayuser/mta-operator-bundle:$bundleversion

../../../../../operator-registry/bin/opm index add --bundles quay.io/$quayuser/mta-operator-bundle:$bundleversion \
--tag quay.io/$quayuser/mta-operator-test-catalog:$bundleversion --container-tool podman

fi

podman push  quay.io/$quayuser/mta-operator-test-catalog:$bundleversion

sleep 20

kubectl delete catalogsource test-catalog -n openshift-marketplace
sed "s/{user}/$quayuser/g" catalog-source.yaml | sed "s/{version}/$bundleversion/g" | kubectl create -f -
