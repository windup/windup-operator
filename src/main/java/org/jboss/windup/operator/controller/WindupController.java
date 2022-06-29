package org.jboss.windup.operator.controller;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.java.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceList;
import org.jboss.windup.operator.util.WindupDeployment;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

@Log
@ApplicationScoped
public class WindupController implements Watcher<WindupResource> {
	@Inject
	MixedOperation<WindupResource, WindupResourceList, Resource<WindupResource>> crClient;

	@Named("namespace")
	String namespace;

	@ConfigProperty(name = "operator.serviceaccount", defaultValue = "windup-operator")
	String serviceAccount;

	@ConfigProperty(name = "operator.sso_public_key")
	String ssoPublicKey;

	@ConfigProperty(name = "operator.application-server-name")
	String applicationServerName;

	@Inject
	KubernetesClient k8sClient;

	private void onAdd(WindupResource resource) {
		log.info("Event ADD " + resource.getMetadata().getName());
		if (!resource.isDeploying() && !resource.isReady()) {
			new WindupDeployment(resource, crClient, k8sClient, namespace, serviceAccount, ssoPublicKey, applicationServerName).deploy();
		}
	}

	private void onUpdate(WindupResource newResource) {
		log.info("Event UPDATE " + newResource.getMetadata().getName() + " - DeploymentsReady "
				+ newResource.deploymentsReady() + " isReady " + newResource.isReady() + " Status "
				+ newResource.getStatus().getConditions());

		consolidateExecutorDeployment(newResource);

		updateCRStatus(newResource);
	}

	private void updateCRStatus(WindupResource newResource) {
		// Consolidate status of the CR
		// retrieving number of Pods ready in the namespace and created by the operator
		long operandsReady = k8sClient.apps().deployments().inNamespace(namespace).withLabel(WindupDeployment.CREATED_BY, WindupDeployment.MTA_OPERATOR).list()
				.getItems().stream().filter(e -> e.getStatus() != null && e.getStatus().getReadyReplicas() != null).mapToInt(e -> e.getStatus().getReadyReplicas()).sum();

		boolean shouldbeReady = operandsReady == newResource.desiredDeployments();

		// if the readyness status changes we updates the CR
		if (newResource.isReady() != shouldbeReady) {
			newResource.setReady(shouldbeReady);
			log.info("Setting this CustomResource as Ready : " + shouldbeReady);
			if (shouldbeReady) newResource.setStatusDeploy(false);
			crClient.inNamespace(namespace).updateStatus(newResource);
		}
	}

	private void consolidateExecutorDeployment(WindupResource newResource) {
		// Consolidating replicas on the executor
		Deployment deploymentExecutor = k8sClient.apps().deployments().inNamespace(namespace)
				.withName(newResource.getMetadata().getName() + "-executor").get();
		if (newResource.getSpec().getExecutor_desired_replicas() != deploymentExecutor.getSpec().getReplicas()) {
			deploymentExecutor.getSpec().setReplicas(newResource.getSpec().getExecutor_desired_replicas());
			k8sClient.apps().deployments().inNamespace(namespace)
					.withName(newResource.getMetadata().getName() + "-executor").patch(deploymentExecutor);
		}
	}

	private void onDelete(WindupResource resource) {
		log.info("Event DELETE [" + resource + "]");
	}

	@Override
	public void eventReceived(Action action, WindupResource resource) {
		log.info("Event received " + action + " received for WindupResource : " + resource.getMetadata().getName());

		switch(action) {
			case ADDED : onAdd(resource); break;
			case MODIFIED : onUpdate(resource); break;
			case DELETED : onDelete(resource); break;
			case ERROR : break;
		}
	}

	@Override
	public void onClose(WatcherException cause) {
		log.info("on close");
		if (cause != null) {
			cause.printStackTrace();
			System.exit(-1);
		}
	}
}