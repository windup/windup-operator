package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.Map;

@KubernetesDependent(labelSelector = WebIngressSecure.LABEL_SELECTOR)
@ApplicationScoped
public class WebIngressSecure extends WebIngressBase {

    public static final String LABEL_SELECTOR = "app.kubernetes.io/managed-by=windup-operator,component=web,component-variant=https";

    @Override
    @SuppressWarnings("unchecked")
    protected Ingress desired(Windup cr, Context<Windup> context) {
        return newIngress(
                cr,
                context,
                getIngressName(cr),
                Map.of(
                        "component", "web",
                        "component-variant", "https"
                ),
                Collections.emptyMap()
        );
    }

    @Override
    public boolean isMet(Windup cr, Ingress ingress, Context<Windup> context) {
        return context.getSecondaryResource(Ingress.class, "ingress-secure")
                .map(in -> {
                    final var status = in.getStatus();
                    if (status != null) {
                        final var ingresses = status.getLoadBalancer().getIngress();
                        // only set the status if the ingress is ready to provide the info we need
                        return ingresses != null && !ingresses.isEmpty();
                    }
                    return false;
                })
                .orElse(false);
    }

    @Override
    protected String getHostname(Windup cr) {
        return CRDUtils
                .getValueFromSubSpec(cr.getSpec().getHostnameSpec(), WindupSpec.HostnameSpec::getHostname)
                .orElseGet(() -> getClusterDomainOnOpenshift()
                        // Openshift
                        .map(domain -> CRDUtils
                                .getValueFromSubSpec(cr.getSpec().getHostnameSpec(), WindupSpec.HostnameSpec::getHostname)
                                .orElseGet(() -> getOpenshiftHostname(cr, k8sClient.getConfiguration().getNamespace(), domain))
                        )
                        // Kubernetes vanilla
                        .orElse(null)
                );
    }

    @Override
    protected IngressTLS getIngressTLS(Windup cr) {
        String tlsSecretName = CRDUtils.getValueFromSubSpec(cr.getSpec().getHttpSpec(), WindupSpec.HttpSpec::getTlsSecret)
                .orElse(null);

        return new IngressTLSBuilder()
                .withSecretName(tlsSecretName)
                .build();
    }

    public static String getIngressName(Windup cr) {
        return cr.getMetadata().getName() + Constants.INGRESS_SECURE_SUFFIX;
    }

    public static String getOpenshiftHostname(Windup cr, String namespace, String domain) {
        return "secure-" + namespace + "-" + cr.getMetadata().getName() + "." + domain;
    }
}
