package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import java.io.InputStream;

@Log
public class KubernetesClientProducer {
    @ConfigProperty(name = "namespace", defaultValue = "rhamt")
    String NAMESPACE;

    @Produces
    @Singleton
    KubernetesClient makeDefaultClient() {
        log.info("Creating K8s Client instance");
        return new DefaultKubernetesClient().inNamespace(NAMESPACE);
    }

    @Produces
    @Singleton
    NonNamespaceOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>>
    makeWindupCustomResource(KubernetesClient defaultClient) {

        KubernetesDeserializer.registerCustomKind("windup.jboss.org/v1beta2", "Windup", WindupResource.class);

        InputStream fileStream = KubernetesClientProducer.class.getResourceAsStream("/k8s/def/windup.crd.yaml");
        log.info("Loading windup.crd.yaml");
        CustomResourceDefinition windupCRD = defaultClient.customResourceDefinitions().load(fileStream).get();
        log.info("Loaded windup.crd.yaml");

        return defaultClient.customResources(windupCRD, WindupResource.class, WindupResourceList.class, WindupResourceDoneable.class).inNamespace(NAMESPACE);

    }

}

