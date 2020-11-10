package org.jboss.windup.operator.model;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class WindupResourceTest {

    @Test
    public void isDeployingTest() {
        WindupResource windupResource = new WindupResource();

        // when no status has been created , it should return false
        assertFalse(windupResource.isDeploying());

        windupResource.getOrAddConditionByType("mydeployment").setReason("whatever");
        assertFalse(windupResource.isDeploying());

        windupResource.getOrAddConditionByType("mydeployment").setReason(WindupResource.DEPLOYMENT).setStatus(Boolean.TRUE.toString());
        assertFalse(windupResource.isDeploying());

        windupResource.setStatusDeploy(false);
        assertFalse(windupResource.isDeploying());

        windupResource.setStatusDeploy(true);
        assertTrue(windupResource.isDeploying());
    }

    @Test
    public void isReadyTest() {
        WindupResource windupResource = new WindupResource();

        assertFalse(windupResource.isReady());

        windupResource.getOrAddConditionByType(WindupResource.READY).setStatus(Boolean.TRUE.toString());
        assertTrue(windupResource.isReady());

        windupResource.getOrAddConditionByType(WindupResource.READY).setStatus(Boolean.FALSE.toString());
        assertFalse(windupResource.isReady());

        windupResource.setReady(true);
        assertTrue(windupResource.isReady());

        windupResource.setReady(false);
        assertFalse(windupResource.isReady());
    }

    @Test
    public void deploymentsReadyTest() {
        WindupResource windupResource = new WindupResource();

        assertEquals(0, windupResource.deploymentsReady());

        windupResource.getOrAddConditionByType("deployment1").setReason(WindupResource.DEPLOYMENT).setStatus(Boolean.TRUE.toString());
        assertEquals(1, windupResource.deploymentsReady());
        windupResource.getOrAddConditionByType("deployment1").setReason(WindupResource.DEPLOYMENT).setMessage("whatever");
        assertEquals(1, windupResource.deploymentsReady());
        windupResource.getOrAddConditionByType("deployment2").setReason(WindupResource.DEPLOYMENT).setMessage("whatever").setStatus(Boolean.TRUE.toString());
        windupResource.getOrAddConditionByType("deployment3").setReason(WindupResource.DEPLOYMENT).setMessage("whatever").setStatus(Boolean.FALSE.toString());
        windupResource.getOrAddConditionByType("deployment4").setReason(WindupResource.DEPLOYMENT).setMessage("whatever");
        assertEquals(2, windupResource.deploymentsReady());
    }


    
}
