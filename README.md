# windup-operator project
This project creates an Operator to deploy Windup/MTA on an Openshift cluster ( Kubernetes in future releases)

It consists on a series of yaml files to deploy the operator (CRD, role, rolebinding, serviceaccount) and the native artifact compiled using Quarkus and GraalVM.

At this moment the operator reacts to creation of the Windup Custom Resource and will deploy all needed objects ( deployments, ingress, services, persistent volumes).




