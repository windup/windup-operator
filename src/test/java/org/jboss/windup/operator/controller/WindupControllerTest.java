package org.jboss.windup.operator.controller;

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.windup.operator.KubernetesCrudRecorderDispatcher;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void onAddTest() throws InterruptedException {
        InputStream fileStream = WindupControllerTest.class.getResourceAsStream("/windup.resource.yaml");
        WindupResource windupResource = Serialization.unmarshal(fileStream, WindupResource.class);
        crClient.inNamespace("test").create(windupResource);

        dispatcher.getRequests().clear();

        Thread.sleep(2000);

        assertEquals(2, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().indexOf("persistentvolumeclaim") >= 0).count());
        assertEquals(3, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().indexOf("deployments") >= 0).count());
        assertEquals(2, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().indexOf("ingress") >= 0).count());
        assertEquals(3, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.getMethod()) && e.getPath().indexOf("service") >= 0).count());

        assertEquals(4, dispatcher.getRequests().stream().filter(e-> "PUT".equalsIgnoreCase(e.getMethod()) && e.getPath().indexOf("status") >= 0).count());

    }
}
