package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.dsl.base.OperationContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.quarkus.runtime.StartupEvent;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;
import org.jboss.windup.operator.model.WindupResourceStatusCondition;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
@Log
public class WindupOperator {
    @ConfigProperty(name = "resyncPeriod", defaultValue = "60000")
    long resyncPeriodInMillis;

    @Inject
    WindupController windupController;

    @Inject
    KubernetesClient k8Client;

    @Inject
    CustomResourceDefinitionContext crdContext;

    @Inject
    MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

    @ConfigProperty(name = "namespace", defaultValue = "mta")
    String NAMESPACE;

    public void onStart(@Observes StartupEvent event) {
        log.info("Startup");

        log.info(" .... Adding Windup informer ....");

        k8Client.informers()
            .sharedIndexInformerForCustomResource(crdContext, WindupResource.class, WindupResourceList.class, resyncPeriodInMillis)
            .addEventHandler(windupController);

        k8Client.informers().sharedIndexInformerFor(Deployment.class, DeploymentList.class,
            new OperationContext().withNamespace(NAMESPACE), resyncPeriodInMillis)
            .addEventHandler(new ResourceEventHandler<Deployment>(){

            @Override
            public void onAdd(Deployment obj) {
                updateCRStatus(obj);
            }

            @Override
            public void onUpdate(Deployment oldObj, Deployment newObj) {
                updateCRStatus(newObj);
            }

            @Override
            public void onDelete(Deployment obj, boolean deletedFinalStateUnknown) {
                updateCRStatus(obj);
            }

            private void updateCRStatus(Deployment obj) {
                // Get the CR name where this deployment belongs to 
                String name = obj.getMetadata().getOwnerReferences()
                                .stream()
                                .filter(OwnerReference::getController)
                                .findFirst()
                                .map(OwnerReference::getName)
                                .orElse("");

                WindupResource cr = crClient.inNamespace(NAMESPACE).withName(name).get();
                // We want 1 replica per deployment, so checking if there is 1 replica Ready
                cr.getStatus().getOrAddConditionByType(name)
                                .setStatus(Boolean.toString(obj.getStatus().getReadyReplicas() == 1));
                // Sending the new status to K8s
                crClient.inNamespace(NAMESPACE).updateStatus(cr);
            }
            });
        k8Client.informers().startAllRegisteredInformers();
    }

}