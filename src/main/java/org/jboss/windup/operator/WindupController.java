package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@Log
@ApplicationScoped
final class WindupController implements ResourceEventHandler<WindupResource> {
    @Inject
	WindupDeploymentJava windupDeployment;

	@Inject
	MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

	@ConfigProperty(name = "namespace", defaultValue = "mta")
	String NAMESPACE;

	@Inject
	KubernetesClient k8sClient;


	@Override
	public void onAdd(WindupResource resource) {
		log.info("Event ADD [" + resource + "]");
		if (!resource.isDeploying()) {
		  windupDeployment.deploy(resource);
		}
	}

	@Override
	public void onUpdate(WindupResource oldResource, WindupResource newResource) {
		log.info("Event UPDATE [" + newResource + "]");

		// Consolidate status of the CR
		if ( newResource.getStatus().deploymentsReady() == 3 && !newResource.getStatus().isReady()) {
			newResource.getStatus().setReady(true);
			newResource.getStatus().getOrAddConditionByType("Deploy").setStatus(Boolean.FALSE.toString());
			crClient.inNamespace(NAMESPACE).updateStatus(newResource);
		}
	}

	@Override
	public void onDelete(WindupResource resource, boolean deletedFinalStateUnknown) {
		log.info("Event DELETE [" + resource + "]");
	}
}