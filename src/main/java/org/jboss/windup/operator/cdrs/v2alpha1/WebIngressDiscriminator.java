package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Optional;

public class WebIngressDiscriminator implements ResourceDiscriminator<Ingress, Windup> {
    @Override
    public Optional<Ingress> distinguish(Class<Ingress> resource, Windup windup, Context<Windup> context) {
        String ingressName = WebIngress.getIngressName(windup);
        ResourceID resourceID = new ResourceID(ingressName, windup.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<Ingress, Windup>) context.eventSourceRetriever().getResourceEventSourceFor(Ingress.class);
        return informerEventSource.get(resourceID);
    }
}
