package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

@KubernetesDependent(labelSelector = DBService.LABEL_SELECTOR)
@ApplicationScoped
public class DBService extends CRUDKubernetesDependentResource<Service, Windup> {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=windup-operator,component=db";

    public DBService() {
        super(Service.class);
    }

    @Override
    public Service desired(Windup cr, Context<Windup> context) {
        return newService(cr, context);
    }

    @SuppressWarnings("unchecked")
    private Service newService(Windup cr, Context<Windup> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new ServiceBuilder()
                .withNewMetadata()
                .withName(getServiceName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .addToLabels("component", "db")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getServiceSpec(cr))
                .build();
    }

    private ServiceSpec getServiceSpec(Windup cr) {
        return new ServiceSpecBuilder()
                .addNewPort()
                .withPort(5432)
                .withProtocol(Constants.SERVICE_PROTOCOL)
                .endPort()
                .withSelector(Constants.DB_SELECTOR_LABELS)
                .withType("ClusterIP")
                .build();
    }

    public static String getServiceName(Windup cr) {
        return cr.getMetadata().getName() + Constants.DB_SERVICE_SUFFIX;
    }

}
