package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.fabric8.kubernetes.api.model.apiextensions.v1.DoneableCustomResourceDefinition;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.quarkus.arc.profile.IfBuildProfile;
import lombok.extern.java.Log;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.InputStream;

@Log
public class KubernetesClientProducer {
    @Produces
    @Singleton
    @Named("namespace")
    String findMyCurrentNamespace(NamespacedKubernetesClient client)  {
        return client.getConfiguration().getNamespace();
    }

    @Produces
    @Singleton
    @IfBuildProfile("prod")
    NamespacedKubernetesClient makeDefaultClient() {
        log.info("Creating K8s Client instance");
        return new DefaultKubernetesClient();
    }

    @Produces
    @Singleton
    MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>>
    makeWindupCustomResource(NamespacedKubernetesClient defaultClient, CustomResourceDefinitionContext crdContext, @Named("namespace") String namespace) {
        log.info("Registering custom kind");
        KubernetesDeserializer.registerCustomKind("windup.jboss.org/v1", "Windup", WindupResource.class);

        return defaultClient.inNamespace(namespace).customResources(crdContext, WindupResource.class, WindupResourceList.class, WindupResourceDoneable.class);
    }

    @Produces
    @Singleton
    CustomResourceDefinitionContext makeCRDContext(NamespacedKubernetesClient defaultClient, @Named("namespace") String namespace) {
        log.info("Creating windup.crd.yaml stream");
        InputStream fileStream = KubernetesClientProducer.class.getResourceAsStream("/k8s/def/windup.crd.yaml");

        log.info("Loading windup.crd.yaml");
        log.info("client : " + defaultClient);
        log.info("crds : " + defaultClient.customResourceDefinitions());
        Resource<CustomResourceDefinition, DoneableCustomResourceDefinition> resource = defaultClient.inNamespace(namespace).apiextensions().v1().customResourceDefinitions().load(fileStream);
        log.info("resource : " + resource);
        CustomResourceDefinition windupCRD = resource.get();
        log.info("Loaded windup.crd.yaml");

        return new CustomResourceDefinitionContext.Builder()
                .withGroup(windupCRD.getSpec().getGroup())
                .withVersion(windupCRD.getSpec().getVersions().stream().findFirst()
                        .map(CustomResourceDefinitionVersion::getName).orElse(""))
                .withScope(windupCRD.getSpec().getScope())
                .withName(windupCRD.getMetadata().getName())
                .withPlural(windupCRD.getSpec().getNames().getPlural())
                .withKind(windupCRD.getSpec().getNames().getKind())
            .build();
    }

}

