package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.java.Log;
import org.jboss.windup.operator.controller.WindupController;
import org.jboss.windup.operator.controller.WindupDeploymentController;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

@ApplicationScoped
@Log
public class WindupOperator {
    @Inject
    WindupController windupController;

    @Inject
    WindupDeploymentController windupDeploymentController;

    @Inject
    KubernetesClient k8Client;

    @Inject
    MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

    @Named("namespace")
    String namespace;

    public void onStart(@Observes StartupEvent event) {
        log.info("Startup");

        log.info("Adding Windup watcher ....");
        crClient.watch(windupController);

        log.info("Adding Windup Deployments watcher ....");
        k8Client.apps().deployments().inNamespace(namespace).watch(windupDeploymentController);
    }

}