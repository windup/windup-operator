package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

@ApplicationScoped
public class Configuration {
    @Produces
    @Singleton
    KubernetesClient newClient() {
      return new DefaultKubernetesClient().inNamespace("default");
    }
}