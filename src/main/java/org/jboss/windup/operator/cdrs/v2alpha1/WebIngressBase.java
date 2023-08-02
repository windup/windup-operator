package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.logging.Log;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class WebIngressBase extends CRUDKubernetesDependentResource<Ingress, Windup> implements Condition<Ingress, Windup> {

    @Inject
    KubernetesClient k8sClient;

    public WebIngressBase() {
        super(Ingress.class);
    }

    protected abstract String getHostname(Windup cr);
    protected abstract IngressTLS getIngressTLS(Windup cr);

    @SuppressWarnings("unchecked")
    protected Ingress newIngress(Windup cr, Context<Windup> context, String ingressName, Map<String, String> additionalLabels, Map<String, String> additionalAnnotations) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        var port = WebService.getServicePort(cr);

        String hostname = getHostname(cr);
        IngressTLS ingressTLS = getIngressTLS(cr);
        List<IngressTLS> ingressTLSList = ingressTLS != null ? Collections.singletonList(ingressTLS) : Collections.emptyList();

        return new IngressBuilder()
                .withNewMetadata()
                    .withName(ingressName)
                    .withNamespace(cr.getMetadata().getNamespace())
                    .withAnnotations(additionalAnnotations)
                    .withLabels(labels)
                    .addToLabels(additionalLabels)
                    .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withNewSpec()
                    .addNewRule()
                        .withHost(hostname)
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/")
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName(WebService.getServiceName(cr))
                                        .withNewPort()
                                        .withNumber(port)
                                        .endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                    .withTls(ingressTLSList)
                .endSpec()
                .build();
    }

    protected String getBaseHostname(Windup cr) {
        String hostname = "";
        final var hostnameSpec = cr.getSpec().getHostnameSpec();
        if (hostnameSpec != null && hostnameSpec.getHostname() != null) {
            hostname = hostnameSpec.getHostname();
        } else {
            hostname = k8sClient.getConfiguration().getNamespace() + "-" +
                    cr.getMetadata().getName() + "." +
                    getClusterDomainOnOpenshift().orElse("");
        }

        return hostname;
    }

    protected Optional<String> getClusterDomainOnOpenshift() {
        String clusterDomain = null;
        try {
            CustomResourceDefinitionContext customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
                    .withName("Ingress")
                    .withGroup("config.openshift.io")
                    .withVersion("v1")
                    .withPlural("ingresses")
                    .withScope("Cluster")
                    .build();
            GenericKubernetesResource clusterObject = k8sClient.genericKubernetesResources(customResourceDefinitionContext)
                    .withName("cluster")
                    .get();

            Map<String, String> objectSpec = Optional.ofNullable(clusterObject)
                    .map(kubernetesResource -> kubernetesResource.<Map<String, String>>get("spec"))
                    .orElse(Collections.emptyMap());
            clusterDomain = objectSpec.get("domain");
        } catch (KubernetesClientException exception) {
            // Nothing to do
            Log.info("No Openshift host found");
        }

        return Optional.ofNullable(clusterDomain);
    }

}
