 # script to delete everything in the cluster related to the Operator
 kubectl delete subscription -A --field-selector metadata.name=windup
 kubectl delete catalogsource -n openshift-marketplace --field-selector metadata.name=test-catalog
 kubectl delete crd windups.windup.jboss.org
 kubectl delete windup --all -A
 kubectl delete deployment -A --field-selector metadata.name=windup-operator.v0.0.1
 kubectl delete csv -A --field-selector metadata.name=windup-operator.v0.0.1