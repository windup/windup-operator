package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import lombok.extern.java.Log;
import org.jboss.windup.operator.model.WindupResource;

import javax.inject.Inject;

@Log
final class WindupController implements Watcher<WindupResource> {
    @Inject
    WindupDeploymentJava windupDeployment;
    
    @Override
	public void eventReceived(Action action, WindupResource resource) {
	    log.info("Event " + action.name());

	    switch (action) {
	        case ADDED:
	            log.info(" .... deploying Windup infrastructure ....");
	            windupDeployment.deployWindup(resource);
	            break;
	        default:
	            break;
	    }
	}

	@Override
	public void onClose(KubernetesClientException e) {
	    // nothing to see here
	}
}