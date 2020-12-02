package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.quarkus.arc.profile.IfBuildProfile;
import lombok.extern.java.Log;
import okhttp3.mockwebserver.MockWebServer;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;

@Log
public class KubernetesTestClientProducer {
    @Produces
    @Singleton
    @IfBuildProfile("test")
    NamespacedKubernetesClient makeDefaultClient(KubernetesMockServer server) {
        log.info("Creating K8s Test Client instance ");
        return server.createClient();
    }

    @Produces
    @Singleton
    @IfBuildProfile("test")
    KubernetesCrudRecorderDispatcher makeDispatcher() {
        return new KubernetesCrudRecorderDispatcher( Collections.emptyList());
    }

    @Produces
    @Singleton
    @IfBuildProfile("test")
    KubernetesMockServer makeKubernetesServer(KubernetesCrudRecorderDispatcher dispatcher) {
        MockWebServer webServer = new MockWebServer();
        KubernetesMockServer kubernetesServer = new KubernetesMockServer(new Context(), webServer, new HashMap<>(), dispatcher, false);
        log.info("Creating K8sServer :" + kubernetesServer);
        return kubernetesServer;
    }

}
