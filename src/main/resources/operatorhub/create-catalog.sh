#!/bin/sh 

# Install operator-registry
# git clone https://github.com/operator-framework/operator-registry
# cd operator-registry
# make build

if [[ $# -eq 0 ]] ; then
    echo 'Usage `./create-catalog.sh -u myuser -p passwd  -m 0.0.2 -b 1`
 -u : quay user 
 -p : quay password 
 -m : version of the Windup Operator
 -b : do the operator build first ? 1 yes , 0 no'
    exit 0
fi

set -x

while getopts u:p:m:b: flag
do
    case "${flag}" in
        u) quayuser=${OPTARG};;
        p) quaypwd=${OPTARG};;
        m) windupoperatorversion=${OPTARG};;
        b) dobuild=${OPTARG};;
    esac
done

bundleversion=$RANDOM

# podman login
podman login quay.io -u $quayuser -p $quaypwd

# Do the build of the operator with current code, if requested
if [ "1" = "$dobuild" ]; then 

cd ../../../..
./mvnw clean package -Pnative -DskipTests \
            -Dquarkus.native.container-build=true -Dquarkus.native.container-runtime=podman \
            -Dquarkus.container-image.push=true \
            -Dquarkus.container-image.tag=$windupoperatorversion -Dquarkus.container-image.group=$quayuser \
            -Dquarkus.container-image.username=$quayuser -Dquarkus.container-image.password=$quaypwd
cd src/main/resources/operatorhub
fi

# Build operator catalog
# if version on argument is the latest version , not published yet, it will create the catalog from the community one to include past published versions
# it will also mean the container image for the operator will be the one on the $user quay account
if [ "0.0.10" == "$windupoperatorversion" ]; then
# Create operator bundle image using user quay image for operator
sed -i "s/quay.io\/windupeng/quay.io\/$quayuser/g" "windup-operator/$windupoperatorversion/manifests/windup-operator.v$windupoperatorversion.clusterserviceversion.yaml"
podman build -f windup-operator/$windupoperatorversion/Dockerfile -t windup-operator-bundle:$bundleversion windup-operator/$windupoperatorversion/
podman tag windup-operator-bundle:$bundleversion quay.io/$quayuser/windup-operator-bundle:$bundleversion

podman push quay.io/$quayuser/windup-operator-bundle:$bundleversion
sed -i "s/quay.io\/$quayuser/quay.io\/windupeng/g" "windup-operator/$windupoperatorversion/manifests/windup-operator.v$windupoperatorversion.clusterserviceversion.yaml"

../../../../../operator-registry/bin/opm index add --bundles quay.io/$quayuser/windup-operator-bundle:$bundleversion \
--tag quay.io/$quayuser/windup-operator-test-catalog:$bundleversion --container-tool podman \
--from-index quay.io/openshift-community-operators/catalog:latest
else
# Using an already published version means we are not going recreate anything, just push the existing community-operators catalog as the test catalog
podman tag quay.io/openshift-community-operators/catalog:latest quay.io/$quayuser/windup-operator-test-catalog:$bundleversion

fi

podman push quay.io/$quayuser/windup-operator-test-catalog:$bundleversion

sleep 20

sed "s/{user}/$quayuser/g" catalog-source.yaml | sed "s/{version}/$bundleversion/g" | kubectl apply -f -
