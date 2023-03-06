package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Optional;

public class WebDeploymentDiscriminator implements ResourceDiscriminator<Deployment, Windup> {
    @Override
    public Optional<Deployment> distinguish(Class<Deployment> resource, Windup windup, Context<Windup> context) {
        String deploymentName = WebDeployment.getDeploymentName(windup);
        ResourceID resourceID = new ResourceID(deploymentName, windup.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<Deployment, Windup>) context.eventSourceRetriever().getResourceEventSourceFor(Deployment.class);
        return informerEventSource.get(resourceID);
    }
}
