# windup-operator project
This project creates an Operator to deploy Windup/MTA on an Openshift cluster ( Kubernetes in future releases)

It consists on a series of yaml files to deploy the operator (CRD, role, rolebinding, serviceaccount) and the native artifact compiled using Quarkus and GraalVM.

At this moment the operator reacts to creation of the Windup Custom Resource and will deploy all needed objects ( deployments, ingress, services, persistent volumes).

# Installation using Openshift 4.4+ OperatorHub
1. Using a Cluster Wide permissions user
2. Go to Openshift Web Console
3. Administration->Operators->OperatorHub
4. Filter by "Migrations"
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

For every namespace you need to install the operator in order to be able to install the Migration Toolkit for Applications application.
