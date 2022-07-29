# windup-operator project

This project creates an Operator to deploy Windup on an Openshift cluster ( Kubernetes in future releases)

It consists on a series of yaml files to deploy the operator (CRD, role, rolebinding, serviceaccount) and the native
artifact compiled using Quarkus and GraalVM.

At this moment the operator reacts to creation of the Windup Custom Resource and will deploy all needed objects (
deployments, ingress, services, persistent volumes).

# Installation using Openshift 4.4+ OperatorHub

1. Using a Cluster Wide permissions user
2. Go to Openshift Web Console
3. Administration->Operators->OperatorHub
4. Filter by keyword "windup"
5. Click on "Migration Toolkit for Applications"
6. Install the operator
7. Select a namespace created previously by you
8. After a while , with a regular user, click on Administration->Operators->Installed Operators
9. Click on the Migration Toolkit for Applications
10. Click on "Create instance"
11. Change the default values or leave them as they are
12. Click "Create"
13. After a while all pods will be ready
14. Go to "Adminitration->Network->Routes" filtering by the namespace used
15. Select the route location
16. This will open a window with the Migration Toolkit for Applications

You can create as many applications as you like inside the same namespace.

For every namespace you need to install the operator in order to be able to install the Migration Toolkit for
Applications application.

## Deploy the operator into Minikube

### Container image of the operator

Execute:

```shell
mvn clean package -DskipTests \
-Dquarkus.container-image.build=true \
-Dquarkus.container-image.registry=quay.io \
-Dquarkus.container-image.group=$USER \
-Dquarkus.container-image.tag=test
```

Push container to the quay.io registry:

```shell
podman push quay.io/$USER/windup-operator-native:test
```

### Minikube

Start Minikube:

```shell
minikube start --memory=12g
minikube addons enable ingress
minikube addons enable dashboard
```

Create the k8s `namespace`:

```shell
kubectl create ns windup
```

### Deploy operator

Point to your custom container image:

```shell
sed -i "s\image: quay.io/windupeng/windup-operator-native:latest\image: quay.io/$USER/windup-operator-native:test\g" src/main/resources/k8s/def/windup.deployment.yaml
```

Create k8s resources:

```shell
cd src/main/resources/k8s/def/
./script.create.all.sh
cd ../../../../../
```

### Init Windup

```shell
kubectl apply -f src/main/resources/k8s/examples/windup.yaml
```

### See your Pods

```shell
minikube dashboard
```