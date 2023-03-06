# Quick start

## Minikube

Start minikube

```shell
minikube start
minikube addons enable ingress
```

### Init Quarkus

This Operator will be installed in the "operators" namespace and will be usable from all namespaces in the cluster.

Create the CRDs:

```shell
mvn clean package -DskipTests
kubectl apply -f target/kubernetes/windups.windup.jboss.org-v1.yml
```

Start the project in dev mode:

```shell
mvn compile quarkus:dev
```

Enable debug in your IDE and then instantiate the operator:

```shell
kubectl apply -f src/main/resources/windup.yml
```

### Publish Operator

Create operator container:

```shell
mvn clean package \
-Dquarkus.native.container-build=true \
-Dquarkus.container-image.build=true \
-Dquarkus.container-image.push=false \
-Dquarkus.container-image.registry=quay.io \
-Dquarkus.container-image.group=projectopenubl \
-Dquarkus.container-image.name=searchpe-operator \
-Dquarkus.operator-sdk.bundle.package-name=searchpe-operator \
-Dquarkus.operator-sdk.bundle.channels=alpha \
-Dquarkus.application.version=test \
-P native
podman push quay.io/$USER/searchpe-operator:nightly
```

Create bundle:

```shell
BUNDLE_IMAGE=quay.io/$USER/searchpe-operator-bundle:test
podman build -t $BUNDLE_IMAGE -f target/bundle/searchpe-operator/bundle.Dockerfile target/bundle/searchpe-operator
podman push $BUNDLE_IMAGE
```

Create catalog image:

```shell
CATALOG_IMAGE=quay.io/$USER/searchpe-operator-catalog:nightly
opm index add \
    --bundles $BUNDLE_IMAGE \
    --tag $CATALOG_IMAGE \
    --build-tool podman
podman push $CATALOG_IMAGE
```

Create catalog:

```shell
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: searchpe-catalog-source
  namespace: openshift-marketplace
spec:
  sourceType: grpc
  image: $CATALOG_IMAGE
EOF
```

Verify:

```shell
kubectl get csv -n operators searchpe-operator
```