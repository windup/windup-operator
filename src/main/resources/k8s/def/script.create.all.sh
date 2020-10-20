echo "Creating namespace"
kubectl apply -f ./windup.namespace.yaml
echo "Creating serviceaccount"
kubectl apply -f ./windup.serviceaccount.yaml
echo "Creating role"
kubectl apply -f ./windup.role.yaml
echo "Creating rolebinding"
kubectl apply -f ./windup.rolebinding.yaml
echo "Creating operator deployment"
kubectl apply -f ./windup.deployment.yaml
echo "Creating CRD"
kubectl apply -f ./windup.crd.yaml
echo "Creating CR ... should start windups deployment"
kubectl apply -f ../examples/windup-v4.0.2.yaml
