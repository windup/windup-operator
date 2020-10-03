package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
public class WindupOperator {
    private Logger logger = Logger.getLogger(WindupOperator.class);

    @Inject
    NonNamespaceOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

    @Inject
    WindupDeployment windupDeployment;

    public void onStart(@Observes StartupEvent event) {
        logger.info("Startup");
        crClient.watch(new Watcher<WindupResource>() {
            @Override
            public void eventReceived(Action action, WindupResource resource) {
                logger.info("Event " + action.name());

                if (action == Action.ADDED) {
                    logger.info(" .... deploying Windup infrastructure ....");
                    try {
                        windupDeployment.deployWindup(resource);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onClose(KubernetesClientException e) {
                // nothing to see here
            }
        });
    }

    void onStop(@Observes ShutdownEvent event) {
        // nothing special
    }
}