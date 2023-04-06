package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = WebIngressDiscriminator.class)
@ApplicationScoped
public class WebIngress extends WebIngressBase {

    @Override
    @SuppressWarnings("unchecked")
    protected Ingress desired(Windup cr, Context<Windup> context) {
        return newIngress(cr, context, getIngressName(cr), Map.of(
                "console.alpha.openshift.io/overview-app-route", "true"
        ));
    }

    @Override
    public boolean isMet(Windup cr, Ingress ingress, Context<Windup> context) {
        return context.getSecondaryResource(Ingress.class, new WebIngressDiscriminator())
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
        return null;
    }

    public static String getIngressName(Windup cr) {
        return cr.getMetadata().getName() + Constants.INGRESS_SUFFIX;
    }

    public static String getOpenshiftHostname(Windup cr, String namespace, String domain) {
        return namespace + "-" + cr.getMetadata().getName() + "." + domain;
    }
}
