package org.jboss.windup.operator.controller;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import lombok.extern.java.Log;
import org.awaitility.Awaitility;
import org.jboss.windup.operator.KubernetesCrudRecorderDispatcher;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;
import org.jboss.windup.operator.model.WindupResourceStatusCondition;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@Log
@QuarkusTest
public class WindupControllerTest {
    @Inject
    KubernetesCrudRecorderDispatcher dispatcher;

    @Inject
    WindupController windupController;

    @Inject
    KubernetesMockServer server;

    @Inject
    MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

    @Inject
    KubernetesClient client;

    @Test
    public void onAddCR_shouldServerReceiveExactCalls() throws InterruptedException {
        InputStream fileStream = WindupControllerTest.class.getResourceAsStream("/windup.resource.yaml");
        WindupResource windupResource = Serialization.unmarshal(fileStream, WindupResource.class);
        crClient.inNamespace("test").create(windupResource);

        dispatcher.getRequests().clear();

        Awaitility
            .await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertEquals(2, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("ingress")).count()));

        assertEquals(4, dispatcher.getRequests().stream().filter(e-> "PUT".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("status") ).count());
        assertEquals(2, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("persistentvolumeclaim")).count());
        assertEquals(3, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("deployments") ).count());
        assertEquals(3, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("service")).count());
    }

    // @format:off
    @Test
    public void onAccCR_orderOfReadyStatusShouldBeTheExpectedOne() throws InterruptedException {
        InputStream fileStream = WindupControllerTest.class.getResourceAsStream("/windup.resource.yaml");
        WindupResource windupResource = Serialization.unmarshal(fileStream, WindupResource.class);

        dispatcher.getRequests().clear();

        crClient.inNamespace("test").create(windupResource);

        Awaitility
        .await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted( () -> {
            assertEquals(3, client.apps().deployments().inNamespace("test").list().getItems().size());
        });

        setDeploymentReadyReplicas("windupapp-postgresql", 1);
        setDeploymentReadyReplicas("windupapp", 1);
        setDeploymentReadyReplicas("windupapp-executor", 1);

        Awaitility
            .await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                WindupResourceList lista = crClient.inNamespace("test").list();
                List<WindupResourceStatusCondition> status = lista.getItems().get(0).getStatus().getConditions();
                log.info("Status : " + status);
                assertEquals(4, status.stream().filter(e -> "True".equalsIgnoreCase(e.getStatus())).count());
                assertEquals(5, status.stream().map(e -> e.getLastTransitionTime()).distinct().count());

                log.info("CR STATUS :  " + status);
            }) ;

    }

    private void setDeploymentReadyReplicas(String name, int replicas) {
        Deployment postgre = client.apps().deployments().inNamespace("test").withName(name).get();
        if (postgre.getStatus() == null) postgre.setStatus(new DeploymentStatus());
        postgre.getStatus().setReadyReplicas(replicas);
        client.apps().deployments().updateStatus(postgre);
    }


}
