# windup-operator project

This project creates an Operator to deploy Windup/MTA on an Openshift cluster ( Kubernetes one in future releases)

It consists on a series of yaml files to deploy the operator (CRD, role, rolebinding, serviceaccount) and the native artifact compiled using Quarkus and GraalVM

At this moment the operator reacts to creation of the Windup Custom Resource and will deploy all needed objects ( deployments, ingress, services, persistent volumes)

## Building and pushing the Java code manually in native mode
1. The following command will use the configuration in the `application.properties` file :  
```
quarkus.container-image.registry=docker.io
quarkus.container-image.group=windup3
quarkus.container-image.name=${quarkus.application.name}-native
quarkus.container-image.tag=latest
quarkus.kubernetes.service-type=load-balancer
quarkus.kubernetes.image-pull-policy=never
quarkus.container-image.builder=docker
namespace=mta
```
2. Execute the maven command:  
`mvn clean package -Pnative -Dquarkus.native.container-build=true -Dquarkus.container-image.push=true`

## Installation

1. We can log in the Openshift cluster using `oc login .....`
2. Move to the `src/main/resources` folder
3. If you are installing the Operator on a cluster without the `mta` namespace , you first should create the namespace with  
  `oc apply -f windup.namespace.yaml`
3. Create all the objects and deployment for the Operator. For convinience there's a file called `script.create.all.sh` that includes the execution of :  
  `windup.serviceaccount.yaml`  
  `windup.role.yaml`  
  `windup.rolebinding.yaml`  
  `windup.deployment.yaml`  
  `windup.crd.yaml`
3. Now you need to create the CR, with your configuration, to tell the Operator to create the infrastructure.  
`oc apply -f ../examples/windup.yaml`


## Github pipeline

This project also includes a Github Action that will be executed in every push and pullrequest in order to check that the Operator will deploy the expected objects.

This pipeline uses a local Minikube , and overrides the images used for the deployments in order to be able to deploy and run without having the resources constraints, as the operator is mainly concerned about the deployment of the objects.


