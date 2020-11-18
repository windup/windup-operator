# windup-operator project
This project creates an Operator to deploy Windup/MTA on an Openshift cluster ( Kubernetes in future releases)

It consists on a series of yaml files to deploy the operator (CRD, role, rolebinding, serviceaccount) and the native artifact compiled using Quarkus and GraalVM.

At this moment the operator reacts to creation of the Windup Custom Resource and will deploy all needed objects ( deployments, ingress, services, persistent volumes).

## Building and pushing the Java code manually in native mode
1. The following command will use the configuration in the `application.properties` file :  
    ```
    quarkus.kubernetes.deployment-target=kubernetes, openshift
    quarkus.native.resources.includes=k8s/def/*.yaml
    quarkus.container-image.registry=quay.io
    quarkus.container-image.group=windupeng
    quarkus.container-image.name=windup-operator-native
    quarkus.container-image.tag=latest
    quarkus.kubernetes.service-type=load-balancer
    quarkus.native.container-runtime=podman
    ```
1. Install JDK 11  
You can use different ways , but SDKMan is very easy https://sdkman.io/install
1. Install Podman  
https://podman.io/getting-started/installation
2. Execute the maven command:  
`./mvnw clean package -Pnative -Dquarkus.native.container-build=true -Dquarkus.container-image.push=true`

## Installation
1. We can log in the Openshift cluster using `oc login .....` . You will need a user with cluster-wide permissions to deploy the CRD.
1. Move to the `src/main/resources/k8s/def` folder
1. These scripts are considering you are installing the Operator in the `mta` namespace.
   If you are installing the Operator on a cluster without the `mta` namespace , you first should create the namespace with  
  `oc apply -f windup.namespace.yaml`
2. In case you want to install the Operator in any other namespace, you would need to change these scripts pointing to that already existing namespace.
3. Create all the objects and deployment for the Operator. For convinience there's a file called `script.create.all.sh` that includes the execution of :  
  `windup.serviceaccount.yaml`  
  `windup.role.yaml`  
  `windup.rolebinding.yaml`  
  `windup.deployment.yaml`  
  `windup.crd.yaml`
1. Now you need to create the CR, with your configuration, to tell the Operator to create the infrastructure.  
`oc apply -f ../examples/windup.yaml`
1. In order to delete the MTA application (web,executor,postgre,volumes, ...) but not the Operator  
`oc delete -f ../examples/windup.yaml`
1. In order to totally delete everything except the namespace, execute `script.delete.all.sh`  
2. NOTE : Do not DELETE a namespace with intentions of creating it again. There are several issues on OCP on deleting a namespace and staying `frozen`


## Github pipeline
This project also includes a Github Action (`.github/workflows/e2e-test.yml`) that will be executed in every pullrequest in order to check that the Operator will deploy the expected objects.

This pipeline uses a local Minikube , and overrides the images used for the deployments in order to be able to deploy and run without having the resources constraints, as the operator is mainly concerned about the deployment of the objects.

## Testing
Project is pushing images to `windupeng` group on Quay.io, and the windup.deployment.yaml to deploy the Operator is also considering this image.  
So, in order to test the operator on your PR review process , or to deploy locally on your cluster, these are the steps you should follow :
1. Log in your Quay.io account  
`podman login quay.io`
1. Build and push the image to your docker hub account  
`./mvnw clean package -Pnative -Dquarkus.native.container-build=true -Dquarkus.container-image.push=true -Dquarkus.container-image.group={your docker id}`
1. Modify the `windup.deployment.yaml` file to point to your image  
`- image: quay.io/windup/windup-operator-native:latest` --> `- image: quay.io/{your docker id}/windup-operator-native:latest`

## Local testing
In order to test the operator with a local cluster ( minikube / Kind ) there's a bash script on `test/resources/local-test.sh`
It will use the current local cluster running and the current context  
It's configured to use `Kind`, but it also includes instructions in order to use `minikube`.




