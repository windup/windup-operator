# windup-operator project

This project uses Quarkus, the Supersonic Subatomic Java Framework to create a Kubernetes Operator following 
[@Alex Soto](https://twitter.com/alexsotob) cheat sheet [Writing a Kubernetes Operator in Java](https://t.co/4m7kSKUPj9?amp=1)


If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
```

## Packaging and running the application

The application can be packaged using `./mvnw package`.
It produces the `windup-operator-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

The application is now runnable using `java -jar target/windup-operator-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: `./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your native executable with: `./target/windup-operator-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image.

## Testing

By default, Pods in Kubernetes do not have the permission to list other pods. Therefore, we need to create a cluster role, a service account, and a cluster role binding.

    kubectl apply -f k8s_files/windup.clusterrole.yaml
    kubectl apply -f k8s_files/windup.serviceaccount.yaml
    kubectl apply -f k8s_files/windup.clusterrolebinding.yaml

Now you can run the `kubectl apply -f k8s_files/windup.crd.yaml` command to register the CRD in the cluster. 

Run the `kubectl apply -f k8s_files/windup.deployment.yaml` command to register the operator.


### Running the example
Apply the custom resource by running: `kubectl apply -f windup-v4.0.2.yaml` and check the output of 
`kubectl get pods` command.

```
> k get pods
NAME                                        READY   STATUS      RESTARTS   AGE
meats-pod                               0/1     Completed   0          7m10s
quarkus-operator-example-554b8f45fc-mcgqn   1/1     Running     0          7m25s
```

If you check the log in the `meats-pod` you can see how the Operator created and delivered our windup ;-)
```
│ __  ____  __  _____   ___  __ ____  ______                                                                                        │
│  --/ __ \/ / / / _ | / _ \/ //_/ / / / __/                                                                                        │
│  -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \                                                                                          │
│ --\___\_\____/_/ |_/_/|_/_/|_|\____/___/                                                                                          │
│ 2020-05-21 14:17:32,324 INFO  [io.quarkus] (main) windup-maker 1.0-SNAPSHOT (powered by Quarkus 1.4.0.CR1) started in 0.027s.      │
│ 2020-05-21 14:17:32,324 INFO  [io.quarkus] (main) Profile prod activated.                                                         │
│ 2020-05-21 14:17:32,324 INFO  [io.quarkus] (main) Installed features: [cdi]                                                       │
│ Doing The Base                                                                                                                    │
│ Adding Sauce bbq                                                                                                                  │
│ Adding Toppings [mozzarella,pepperoni,tuna,mushrooms]                                                                             │
│ Baking                                                                                                                            │
│ Baked                                                                                                                             │
│ Ready For Delivery                                                                                                                │
│ 2020-05-21 14:17:32,825 INFO  [io.quarkus] (main) windup-maker stopped in 0.001s                                                   │
│
```

