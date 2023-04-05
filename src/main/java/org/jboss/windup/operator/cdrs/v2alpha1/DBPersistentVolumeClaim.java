package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = DBPersistentVolumeClaimDiscriminator.class)
@ApplicationScoped
public class DBPersistentVolumeClaim extends CRUDKubernetesDependentResource<PersistentVolumeClaim, Windup>
        implements Creator<PersistentVolumeClaim, Windup> {

    public DBPersistentVolumeClaim() {
        super(PersistentVolumeClaim.class);
    }

    @Override
    protected PersistentVolumeClaim desired(Windup cr, Context<Windup> context) {
        return newPersistentVolumeClaim(cr, context);
    }

    @SuppressWarnings("unchecked")
    private PersistentVolumeClaim newPersistentVolumeClaim(Windup cr, Context<Windup> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        String pvcStorageSize = CRDUtils.getValueFromSubSpec(cr.getSpec().getDatabaseSpec(), WindupSpec.DatabaseSpec::getSize)
                .orElse(Constants.POSTGRESQL_PVC_SIZE);

        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(getPersistentVolumeClaimName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(new PersistentVolumeClaimSpecBuilder()
                        .withAccessModes("ReadWriteOnce")
                        .withResources(new ResourceRequirementsBuilder()
                                .withRequests(Map.of("storage", new Quantity(pvcStorageSize)))
                                .build()
                        )
                        .build()
                )
                .build();
    }

    @Override
    public Matcher.Result<PersistentVolumeClaim> match(PersistentVolumeClaim actual, Windup cr, Context<Windup> context) {
        final var desiredPersistentVolumeClaimName = getPersistentVolumeClaimName(cr);
        return Matcher.Result.nonComputed(actual
                .getMetadata()
                .getName()
                .equals(desiredPersistentVolumeClaimName)
        );
    }

    public static String getPersistentVolumeClaimName(Windup cr) {
        return cr.getMetadata().getName() + Constants.DB_PVC_SUFFIX;
    }

}
