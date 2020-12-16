package org.jboss.windup.operator.controller;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;
import org.jboss.windup.operator.util.WindupDeployment;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

@Log
@ApplicationScoped
public class WindupController implements Watcher<WindupResource> {
	@Inject
	MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

	@Named("namespace")
	String namespace;

	@ConfigProperty(name = "operator.serviceaccount", defaultValue = "windup-operator")
	String serviceAccount;

	@ConfigProperty(name = "operator.sso_public_key")
	String ssoPublicKey;

	@Inject
	KubernetesClient k8sClient;

	private void onAdd(WindupResource resource) {
		log.info("Event ADD " + resource.getMetadata().getName());
		if (!resource.isDeploying() && !resource.isReady()) {
			new WindupDeployment(resource, crClient, k8sClient, namespace, serviceAccount, ssoPublicKey).deploy();
		}
	}

	private void onUpdate(WindupResource newResource) {
		log.info("Event UPDATE " + newResource.getMetadata().getName() + " - DeploymentsReady "
				+ newResource.deploymentsReady() + " isReady " + newResource.isReady() 
				+ " Status " + newResource.getStatus().getConditions());

		// retrieving number of Deployments ready in the namespace and created by the operator
		long operandsReady = k8sClient.apps().deployments().inNamespace(namespace).withLabel(WindupDeployment.CREATED_BY, WindupDeployment.MTA_OPERATOR).list()
				.getItems().stream().filter(e -> e.getStatus() != null && e.getStatus().getReadyReplicas() == 1).count();

		if (operandsReady == 3) {
			newResource.setReady(true);
			newResource.setStatusDeploy(false);

			log.info("Setting this CustomResource as Ready");
			crClient.inNamespace(namespace).updateStatus(newResource);
		}
	}

	private void onDelete(WindupResource resource) {
		log.info("Event DELETE [" + resource + "]");
	}

	@Override
	public void eventReceived(Action action, WindupResource resource) {
		log.info("Event received " + action + " received for WindupResource : " + resource.getMetadata().getName());

		if (action == Action.ADDED) onAdd(resource);
		if (action == Action.MODIFIED) onUpdate(resource);
		if (action == Action.DELETED) onDelete(resource);
	}

	@Override
	public void onClose(KubernetesClientException cause) {
		if (cause != null) {
			log.info("on close");
			cause.printStackTrace();
			System.exit(-1);
		}
	}
}