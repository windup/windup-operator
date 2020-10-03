package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.RequestConfig;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import org.jboss.logging.Logger;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class KubernetesClientProducer {
    private Logger log = Logger.getLogger(KubernetesClientProducer.class);

    @Produces
    @Singleton
    @Named("namespace")
    String findMyCurrentNamespace() throws IOException {
        return new String(Files.readAllBytes(Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")));
    }

    @Produces
    @Singleton
    KubernetesClient makeDefaultClient() {
        log.info("Creating K8s Client instance");
        KubernetesClient k8sclient = new DefaultKubernetesClient().inNamespace("default");
        return k8sclient;
    }

    @Produces
    @Singleton
    NonNamespaceOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>>
    makeWindupCustomResource(KubernetesClient defaultClient) {

        KubernetesDeserializer.registerCustomKind("windup.jboss.org/v1beta2", "Windup", WindupResource.class);

        CustomResourceDefinition crd = defaultClient.customResourceDefinitions()
        .list()
        .getItems()
        .stream()
            .filter(d -> "windups.windup.jboss.org".equals(d.getMetadata().getName()))
            .findAny()
                .orElseThrow(() -> new RuntimeException("Deployment error: Custom resource definition windup.jboss.org/v1beta2 not found."));

        return defaultClient.customResources(crd, WindupResource.class, WindupResourceList.class, WindupResourceDoneable.class).inNamespace("default");

    }

}

