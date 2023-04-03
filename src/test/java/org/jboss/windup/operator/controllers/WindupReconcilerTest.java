package org.jboss.windup.operator.controllers;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.windup.operator.cdrs.v2alpha1.DBDeployment;
import org.jboss.windup.operator.cdrs.v2alpha1.DBService;
import org.jboss.windup.operator.cdrs.v2alpha1.ExecutorDeployment;
import org.jboss.windup.operator.cdrs.v2alpha1.WebDeployment;
import org.jboss.windup.operator.cdrs.v2alpha1.WebIngress;
import org.jboss.windup.operator.cdrs.v2alpha1.WebService;
import org.jboss.windup.operator.cdrs.v2alpha1.Windup;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class WindupReconcilerTest {

    public static final String TEST_APP = "test-app";

    @Inject
    KubernetesClient client;

    @Inject
    Operator operator;

    @BeforeEach
    void startOperator() {
        operator.start();
    }

    @AfterEach
    void stopOperator() {
        operator.stop();
    }

    @Test
    @Order(1)
    void reconcileShouldWork() {
        final var app = new Windup();
        final var metadata = new ObjectMetaBuilder()
                .withName(TEST_APP)
                .withNamespace(client.getNamespace())
                .build();
        app.setMetadata(metadata);
        app.getSpec().setDatabaseSpec(WindupSpec.DatabaseSpec.builder()
                .size("0.5Gi")
                .resourceLimitSpec(WindupSpec.ResourcesLimitSpec.builder()
                        .cpuRequest("0.1")
                        .cpuLimit("0.5")
                        .memoryRequest("0.1Gi")
                        .memoryLimit("0.5Gi")
                        .build()
                )
                .build()
        );

        client.resource(app).create();

        await()
                .ignoreException(NullPointerException.class)
                .atMost(20, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    // Database Deployment
                    final var dbDeployment = client.apps()
                            .deployments()
                            .inNamespace(metadata.getNamespace())
                            .withName(DBDeployment.getDeploymentName(app))
                            .get();
                    final var dbContainer = dbDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .stream()
                            .findFirst();
                    assertThat(dbContainer.isPresent(), is(true));
                    assertThat(dbContainer.get().getImage(), is("registry.access.redhat.com/rhscl/postgresql-10-rhel7:1"));

                    assertEquals(1, dbDeployment.getStatus().getReadyReplicas());

                    // Database service
                    final var dbService = client.services()
                            .inNamespace(metadata.getNamespace())
                            .withName(DBService.getServiceName(app))
                            .get();
                    final var dbPort = dbService.getSpec()
                            .getPorts()
                            .get(0)
                            .getPort();
                    assertThat(dbPort, is(5432));

                    // Web Deployment
                    final var webDeployment = client.apps()
                            .deployments()
                            .inNamespace(metadata.getNamespace())
                            .withName(WebDeployment.getDeploymentName(app))
                            .get();
                    final var webContainer = webDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .stream()
                            .findFirst();
                    assertThat(webContainer.isPresent(), is(true));
                    assertThat(webContainer.get().getImage(), is("quay.io/windupeng/windup-web-openshift:latest"));
                    List<Integer> webContainerPorts = webContainer.get().getPorts().stream()
                            .map(ContainerPort::getContainerPort)
                            .toList();
                    assertTrue(webContainerPorts.contains(8080));
                    assertTrue(webContainerPorts.contains(8888));
                    assertTrue(webContainerPorts.contains(8778));

                    assertEquals(1, webDeployment.getStatus().getReadyReplicas());

                    // Web service
                    final var webService = client.services()
                            .inNamespace(metadata.getNamespace())
                            .withName(WebService.getServiceName(app))
                            .get();
                    final var webServicePorts = webService.getSpec()
                            .getPorts()
                            .stream()
                            .map(ServicePort::getPort)
                            .toList();
                    assertTrue(webServicePorts.contains(8080));

                    // Executor Deployment
                    final var executorDeployment = client.apps()
                            .deployments()
                            .inNamespace(metadata.getNamespace())
                            .withName(ExecutorDeployment.getDeploymentName(app))
                            .get();
                    final var executorContainer = executorDeployment.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .stream()
                            .findFirst();
                    assertThat(executorContainer.isPresent(), is(true));
                    assertThat(executorContainer.get().getImage(), is("quay.io/windupeng/windup-web-openshift-messaging-executor:latest"));

                    assertEquals(1, executorDeployment.getStatus().getReadyReplicas());

                    // Ingress
                    final var ingress = client.network().v1().ingresses()
                            .inNamespace(metadata.getNamespace())
                            .withName(WebIngress.getIngressName(app))
                            .get();

                    final var rules = ingress.getSpec().getRules();
                    assertThat(rules.size(), is(1));

                    final var paths = rules.get(0).getHttp().getPaths();
                    assertThat(paths.size(), is(1));

                    final var path = paths.get(0);
//                    assertThat(path.getPath(), is("/"));
//                    assertThat(path.getPathType(), is("Prefix"));

                    final var serviceBackend = path.getBackend().getService();
                    assertThat(serviceBackend.getName(), is(WebService.getServiceName(app)));
                    assertThat(serviceBackend.getPort().getNumber(), is(8080));
                });
    }
}