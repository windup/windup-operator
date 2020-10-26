package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.jboss.logging.Logger;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

public class KubernetesClientProducer {
    private Logger log = Logger.getLogger(KubernetesClientProducer.class);

    @Produces
    @Singleton
    KubernetesClient makeDefaultClient() {
        log.info("Creating K8s Client instance");
        return new DefaultKubernetesClient().inNamespace(WindupDeploymentJava.NAMESPACE);
    }

    @Produces
    @Singleton
    NonNamespaceOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>>
    makeWindupCustomResource(KubernetesClient defaultClient) {

        KubernetesDeserializer.registerCustomKind("windup.jboss.org/v1beta2", "Windup", WindupResource.class);

        CustomResourceDefinition windupCRD = defaultClient.customResourceDefinitions().load("windup.crd.yaml").get();

        return defaultClient.customResources(windupCRD, WindupResource.class, WindupResourceList.class, WindupResourceDoneable.class).inNamespace(WindupDeploymentJava.NAMESPACE);

    }

}

