kubectl delete all -l application=windup --all-namespaces
kubectl delete all -l application=windup-jon --all-namespaces
kubectl delete persistentvolumeclaim -l application=windup-jon --all-namespaces
kubectl delete serviceaccounts -l application=windup
kubectl delete clusterrolebinding -l application=windup
kubectl delete clusterrole -l application=windup
kubectl delete customresourcedefinition -l application=windup

