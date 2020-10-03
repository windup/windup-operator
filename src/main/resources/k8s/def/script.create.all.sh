kubectl apply -f ./windup.clusterrole.yaml
kubectl apply -f ./windup.clusterrolebinding.yaml
kubectl apply -f ./windup.serviceaccount.yaml
kubectl apply -f ./windup.deployment.yaml
kubectl apply -f ./windup.crd.yaml
kubectl apply -f ../examples/windup-v4.0.2.yaml
