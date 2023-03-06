package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Optional;

public class DBPersistentVolumeClaimDiscriminator implements ResourceDiscriminator<PersistentVolumeClaim, Windup> {
    @Override
    public Optional<PersistentVolumeClaim> distinguish(Class<PersistentVolumeClaim> resource, Windup windup, Context<Windup> context) {
        String persistentVolumeClaimName = DBPersistentVolumeClaim.getPersistentVolumeClaimName(windup);
        ResourceID resourceID = new ResourceID(persistentVolumeClaimName, windup.getMetadata().getNamespace());
        var informerEventSource = (InformerEventSource<PersistentVolumeClaim, Windup>) context.eventSourceRetriever().getResourceEventSourceFor(PersistentVolumeClaim.class);
        return informerEventSource.get(resourceID);
    }
}
