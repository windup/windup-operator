package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

@ApplicationScoped
public class WindupOperator {

    @Inject
    NonNamespaceOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

    public void onStart(@Observes StartupEvent event) {
        System.out.println("Startup");
        crClient.watch(new Watcher<WindupResource>() {
            @Override
            public void eventReceived(Action action, WindupResource resource) {
                System.out.println("Event " + action.name() + " .... deploying Windup infrastructure");
                new WindupDeployment().deployWindup();
            }

            @Override
            public void onClose(KubernetesClientException e) {
            }
        });
    }


    void onStop(@Observes ShutdownEvent event) {
        // nothing special
    }
}