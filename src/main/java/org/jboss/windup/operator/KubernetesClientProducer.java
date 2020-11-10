package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1beta1.DoneableCustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.quarkus.arc.profile.IfBuildProfile;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import java.io.InputStream;

@Log
public class KubernetesClientProducer {
    @ConfigProperty(name = "operator.namespace", defaultValue = "mta")
    String namespace;

    @Produces
    @Singleton
    @IfBuildProfile("prod")
    KubernetesClient makeDefaultClient() {
        log.info("Creating K8s Client instance");
        return new DefaultKubernetesClient().inNamespace(namespace);
    }

    @Produces
    @Singleton
    MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>>
    makeWindupCustomResource(KubernetesClient defaultClient, CustomResourceDefinitionContext crdContext) {
        log.info("Registering custom kind");
        KubernetesDeserializer.registerCustomKind("windup.jboss.org/v1", "Windup", WindupResource.class);

        return defaultClient.customResources(crdContext, WindupResource.class, WindupResourceList.class, WindupResourceDoneable.class);
    }

    @Produces
    @Singleton
    CustomResourceDefinitionContext makeCRDContext(KubernetesClient defaultClient) {
        log.info("Creating windup.crd.yaml stream");
        InputStream fileStream = KubernetesClientProducer.class.getResourceAsStream("/k8s/def/windup.crd.yaml");

        log.info("Loading windup.crd.yaml");
        log.info("client : " + defaultClient);
        log.info("crds : " + defaultClient.customResourceDefinitions());
        Resource<CustomResourceDefinition, DoneableCustomResourceDefinition> resource = defaultClient.customResourceDefinitions().load(fileStream);
        log.info("resource : " + resource);
        CustomResourceDefinition windupCRD = resource.get();
        log.info("Loaded windup.crd.yaml");

        return CustomResourceDefinitionContext.fromCrd(windupCRD);
    }

}

