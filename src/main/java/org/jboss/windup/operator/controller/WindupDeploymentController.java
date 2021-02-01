package org.jboss.windup.operator.controller;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

@Log
@ApplicationScoped
public class WindupDeploymentController implements Watcher<Deployment> {
    @Inject
    MixedOperation<WindupResource, WindupResourceList, Resource<WindupResource>> crClient;

    @Named("namespace")
    String namespace;

    @Override
    public void eventReceived(Action action, Deployment deployment) {
        log.info("Event " + action + " received for Deployment : " + deployment.getMetadata().getName());

        updateCRStatus(deployment);
    }

    @Override
    public void onClose(WatcherException cause) {
        // TODO Auto-generated method stub
    }

    private void updateCRStatus(Deployment obj) {
        log.info("Entering on Updating Status for CR of the Deployment : " + obj.getMetadata().getName() + "\n status : " + obj.getStatus());

        // Get the CR name where this deployment belongs to
        String crName = obj.getMetadata().getOwnerReferences()
            .stream()
            .filter(OwnerReference::getController)
            .findFirst()
            .map(OwnerReference::getName)
            .orElse("");

        if (!StringUtils.isBlank(crName)) {
            WindupResource cr = crClient.inNamespace(namespace).withName(crName).get();
            if (cr.getStatus() == null) {
                cr.initStatus();
            }

            log.info("Updating CR Status considering Deployment status : " + obj.getMetadata().getName());
            int desiredReplicas = (obj.getMetadata().getName().contains("-executor") && cr.getSpec().getExecutor_desired_replicas() != null) ? cr.getSpec().getExecutor_desired_replicas() : 1;

            // We want 1 replica per deployment, so checking if there is 1 replica Ready
            Boolean deploymentStatus = (obj.getStatus() != null && obj.getStatus().getReadyReplicas() != null
                    && obj.getStatus().getReadyReplicas() == desiredReplicas);
            cr.setLabelProperty(deploymentStatus, obj.getMetadata().getName(), WindupResource.DEPLOYMENT);

            // Sending the new status to K8s
            crClient.inNamespace(namespace).updateStatus(cr);
        }
    }
}