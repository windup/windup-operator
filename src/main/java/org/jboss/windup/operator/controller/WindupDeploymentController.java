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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
		log.info("on close");
		if (cause != null) {
			cause.printStackTrace();
			System.exit(-1);
        }
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
            // Changing the lastTransitionTime and Sending the status change message to the CR , in order to execute the Cr.update event
            cr.getConditionByType("Ready").ifPresent(e -> e.setLastTransitionTime(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)));
            crClient.inNamespace(namespace).updateStatus(cr);
        }
    }
}