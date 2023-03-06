package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Optional;

public class DBServiceDiscriminator implements ResourceDiscriminator<Service, Windup> {
    @Override
    public Optional<Service> distinguish(Class<Service> resource, Windup windup, Context<Windup> context) {
        String serviceName = DBService.getServiceName(windup);
        ResourceID resourceID = new ResourceID(serviceName, windup.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<Service, Windup>) context.eventSourceRetriever().getResourceEventSourceFor(Service.class);
        return informerEventSource.get(resourceID);
    }
}
