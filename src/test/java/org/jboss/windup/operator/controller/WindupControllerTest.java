package org.jboss.windup.operator.controller;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.jboss.windup.operator.KubernetesCrudRecorderDispatcher;
import org.jboss.windup.operator.Request;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceList;
import org.jboss.windup.operator.model.WindupResourceStatusCondition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class WindupControllerTest {
    private static final String testCRDeployment = "/windup-test.yaml";

    private static final String testApp = "winduptest";

    private static final String testNamespace = "test"; // hardcoded in the KubernetesMockServer.createClient

    @Inject
    KubernetesCrudRecorderDispatcher dispatcher;

    @Inject
    WindupController windupController;

    @Inject
    KubernetesMockServer server;

    @Inject
    MixedOperation<WindupResource, WindupResourceList, Resource<WindupResource>> crClient;

    @Inject
    KubernetesClient client;

    private WindupResource windupResource;

    private void loadWindupResource() {
        InputStream fileStream = WindupControllerTest.class.getResourceAsStream(testCRDeployment);
        windupResource = Serialization.unmarshal(fileStream, WindupResource.class);
        windupResource.getMetadata().setNamespace(testNamespace);
        windupResource.getMetadata().setUid("uid: 4e4d714c-6d27-41e1-86df-4a58900ca5d0");
    }

    @BeforeEach
    public void clean() {
        if (crClient.inNamespace(testNamespace).withName(testApp).get() != null) {
            crClient.inNamespace(testNamespace).withName(testApp).delete();
            Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertNull(crClient.inNamespace(testNamespace).withName(testApp).get()));
        }
        dispatcher.setRequests(new ArrayList<Request>());
        if (windupResource == null) {
            loadWindupResource();
        }
        crClient.inNamespace(testNamespace).create(windupResource);
    }

    @Test
    public void onAddCR_shouldServerReceiveExactCalls() throws InterruptedException {
        Awaitility
        .await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
                assertEquals(2, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("ingress")).count());
                assertEquals(2, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("persistentvolumeclaim")).count());
                assertEquals(3, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("deployments") ).count());
                assertEquals(3, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("service")).count());
                // status changes : 1 on deployment=true, 3 on each deployment status change
                assertEquals(4, dispatcher.getRequests().stream().filter(e-> "PUT".equalsIgnoreCase(e.getMethod()) && e.getPath().contains("status") ).count());
            });
    }

    // @format:off
    @Test
    public void onAccCR_orderOfReadyStatusShouldBeTheExpectedOne() throws InterruptedException {

        // We create another deployment not related with the operator
        client.apps().deployments().inNamespace(testNamespace).create(new DeploymentBuilder()
            .withNewMetadata()
                .withName("test-deployment")
            .endMetadata()
            .withNewSpec()
                .withNewTemplate()
                    .withNewMetadata()
                        .withName("test-pod")
                    .endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withImage("helloworld")
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build());

        Awaitility
        .await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted( () -> {
            // 4 objects expected : operator, mta-web, mta-executor, mta-postgresql, test-deployment
            assertEquals(4, client.apps().deployments().inNamespace(testNamespace).list().getItems().size());
        });

        LocalDateTime startUpdateTime = LocalDateTime.now();
        setDeploymentReadyReplicas(testApp + "-postgresql", 1);
        Thread.sleep(200);
        setDeploymentReadyReplicas(testApp, 1);
        Thread.sleep(200);
        setDeploymentReadyReplicas(testApp + "-executor", 1);
        setDeploymentReadyReplicas("test-deployment", 1);

        Awaitility
            .await()
            .atMost(40, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                WindupResourceList lista = crClient.inNamespace(testNamespace).list();
                List<WindupResourceStatusCondition> status = lista.getItems().get(0).getStatus().getConditions();

                // Checking there are only 2 status in the CR  : Deployment, Ready
                assertEquals(2, status.size());

                // Checking the update time is the expected
                LocalDateTime lastUpdateTime = getLastTransitionTimeFromStatus(status, "Ready");
                assertTrue(lastUpdateTime.isAfter(startUpdateTime.plus(Duration.ofMillis(400))));
            });

    }

    @Test
    public void onUpdateCR_numberOfExecutorPodsShouldBeSameAsInCR() {

        Awaitility
        .await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted( () -> {
            assertEquals(3, client.apps().deployments().inNamespace(testNamespace).list().getItems().size());
        });

        setDeploymentReadyReplicas(testApp + "-postgresql", 1);
        setDeploymentReadyReplicas(testApp, 1);
        setDeploymentReadyReplicas(testApp + "-executor", 1);

        // now lets update the CR
        int desiredReplicas = 3;
        windupResource.getSpec().setExecutor_desired_replicas(desiredReplicas);
        crClient.inNamespace("test").withName(windupResource.getMetadata().getName()).patch(windupResource);

        // if everything went fine, the operator should have received the CR update event and 
        // will send an update on the Deployment object for the executor
        Awaitility
        .await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted( () -> {
                    assertTrue(dispatcher.getRequests().stream().anyMatch(e -> "PATCH".equalsIgnoreCase(e.getMethod()) &&
                                                                e.getPath().contains("deployments/winduptest-executor") &&
                                                                e.getBody().contains("/spec/replicas\",\"value\":" + desiredReplicas)));
        });
    }

    private LocalDateTime getLastTransitionTimeFromStatus(List<WindupResourceStatusCondition> status, String condition_type) {
        return status.stream()
                .filter(e -> condition_type.equalsIgnoreCase(e.getType())).findFirst()
                    .map(e -> LocalDateTime.parse(e.getLastTransitionTime(), DateTimeFormatter.ISO_DATE_TIME))
                    .get();
    }

    private void setDeploymentReadyReplicas(String name, int replicas) {
        Deployment deployment = client.apps().deployments().inNamespace("test").withName(name).get();
        if (deployment.getStatus() == null) deployment.setStatus(new DeploymentStatus());
        deployment.getStatus().setReadyReplicas(replicas);
        client.apps().deployments().updateStatus(deployment);
    }


}
