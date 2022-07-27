#!/bin/sh -x
# you need to have minikube/kind running
# be sure to verify you are in the right context with `kubectl config current-context`
# you can change it with `kubectl config use-context`

cd ../../..
containertag=$RANDOM
echo "containertag : $containertag"
mvn clean package -Pnative -Dquarkus.container-image.build=true -DskipTests "-Dquarkus.container-image.tag=$containertag"
# for minikube replace next line with : eval $(minikube -p minikube docker-env)
kind load docker-image quay.io/windupeng/windup-operator-native:$containertag

cd src/main/resources/k8s/def 
./script.delete.all.sh
kubectl apply -f windup.namespace.yaml 
kubectl apply -f windup.serviceaccount.yaml 
kubectl apply -f windup.role.yaml 
kubectl apply -f windup.rolebinding.yaml 
kubectl apply -f windup.crd.yaml 
sed "s/windup-operator-native:latest/windup-operator-native:$containertag/g" ../../../../test/resources/windup.deployment.yaml | kubectl apply -f -
kubectl apply -f ../../../../test/resources/windup-test.yaml
sleep 20
kubectl get all,ing,pvc -n windup -o name
num=`kubectl get all,ing,pvc -n windup -o name | wc -l`
# 4 deployments (including operator), 2 ingresses, 3 services, 2 pvc, 4 pods (including operator), 4 replicaset
echo "num $num"
if [ "$num" -gt "19" ];
  then echo "Test not passed";
fi
if [ "$num" -eq "19" ];
  then echo "Test OK";
fi