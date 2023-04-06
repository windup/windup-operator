package org.jboss.windup.operator.controllers;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.jboss.logging.Logger;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.cdrs.v2alpha1.DBDeployment;
import org.jboss.windup.operator.cdrs.v2alpha1.DBPersistentVolumeClaim;
import org.jboss.windup.operator.cdrs.v2alpha1.DBSecret;
import org.jboss.windup.operator.cdrs.v2alpha1.DBService;
import org.jboss.windup.operator.cdrs.v2alpha1.ExecutorDeployment;
import org.jboss.windup.operator.cdrs.v2alpha1.WebConsolePersistentVolumeClaim;
import org.jboss.windup.operator.cdrs.v2alpha1.WebDeployment;
import org.jboss.windup.operator.cdrs.v2alpha1.WebIngress;
import org.jboss.windup.operator.cdrs.v2alpha1.WebIngressSecure;
import org.jboss.windup.operator.cdrs.v2alpha1.WebService;
import org.jboss.windup.operator.cdrs.v2alpha1.Windup;

import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;
import static org.jboss.windup.operator.controllers.WindupReconciler.DEPLOYMENT_EVENT_SOURCE;
import static org.jboss.windup.operator.controllers.WindupReconciler.INGRESS_EVENT_SOURCE;
import static org.jboss.windup.operator.controllers.WindupReconciler.PVC_EVENT_SOURCE;
import static org.jboss.windup.operator.controllers.WindupReconciler.SERVICE_EVENT_SOURCE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "windup",
        dependents = {
                @Dependent(name = "db-pvc", type = DBPersistentVolumeClaim.class, useEventSourceWithName = PVC_EVENT_SOURCE),
                @Dependent(name = "db-secret", type = DBSecret.class),
                @Dependent(name = "db-deployment", type = DBDeployment.class, dependsOn = {"db-pvc", "db-secret"}, useEventSourceWithName = DEPLOYMENT_EVENT_SOURCE, readyPostcondition = DBDeployment.class),
                @Dependent(name = "db-service", type = DBService.class, dependsOn = {"db-deployment"}, useEventSourceWithName = SERVICE_EVENT_SOURCE),
                @Dependent(name = "web-pvc", type = WebConsolePersistentVolumeClaim.class, useEventSourceWithName = PVC_EVENT_SOURCE),
                @Dependent(name = "web-deployment", type = WebDeployment.class, dependsOn = {"db-deployment", "db-service", "web-pvc"}, useEventSourceWithName = DEPLOYMENT_EVENT_SOURCE, readyPostcondition = WebDeployment.class),
                @Dependent(name = "web-service", type = WebService.class, dependsOn = {"web-deployment"}, useEventSourceWithName = SERVICE_EVENT_SOURCE),
                @Dependent(name = "executor-deployment", type = ExecutorDeployment.class, dependsOn = {"web-service"}, useEventSourceWithName = DEPLOYMENT_EVENT_SOURCE),
                @Dependent(name = "ingress", type = WebIngress.class, dependsOn = {"executor-deployment"}, readyPostcondition = WebIngress.class, useEventSourceWithName = INGRESS_EVENT_SOURCE),
                @Dependent(name = "ingress-secure", type = WebIngressSecure.class, dependsOn = {"executor-deployment"}, readyPostcondition = WebIngressSecure.class, useEventSourceWithName = INGRESS_EVENT_SOURCE)
        }
)
public class WindupReconciler implements Reconciler<Windup>, ContextInitializer<Windup>,
        EventSourceInitializer<Windup> {

    private static final Logger logger = Logger.getLogger(WindupReconciler.class);

    public static final String PVC_EVENT_SOURCE = "PVCEventSource";
    public static final String DEPLOYMENT_EVENT_SOURCE = "DeploymentEventSource";
    public static final String SERVICE_EVENT_SOURCE = "ServiceEventSource";
    public static final String INGRESS_EVENT_SOURCE = "IngressEventSource";

    @Override
    public void initContext(Windup cr, Context<Windup> context) {
        final var labels = Map.of(
                "app.kubernetes.io/managed-by", "windup-operator",
                "app.kubernetes.io/name", cr.getMetadata().getName(),
                "app.kubernetes.io/part-of", cr.getMetadata().getName(),
                "windup-operator/cluster", Constants.WINDUP_NAME
        );
        context.managedDependentResourceContext().put(Constants.CONTEXT_LABELS_KEY, labels);
    }

    @SuppressWarnings("unchecked")
    @Override
    public UpdateControl<Windup> reconcile(Windup cr, Context context) {
        return context.managedDependentResourceContext()
                .getWorkflowReconcileResult()
                .map(wrs -> {
                    if (wrs.allDependentResourcesReady()) {
                        return UpdateControl.<Windup>noUpdate();
                    } else {
                        final var duration = Duration.ofSeconds(5);
                        return UpdateControl.<Windup>noUpdate().rescheduleAfter(duration);
                    }
                })
                .orElseThrow();
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<Windup> context) {
        var pcvInformerConfiguration = InformerConfiguration.from(PersistentVolumeClaim.class, context).build();
        var deploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context).build();
        var serviceInformerConfiguration = InformerConfiguration.from(Service.class, context).build();
        var ingressInformerConfiguration = InformerConfiguration.from(Ingress.class, context).build();

        var pcvInformerEventSource = new InformerEventSource<>(pcvInformerConfiguration, context);
        var deploymentInformerEventSource = new InformerEventSource<>(deploymentInformerConfiguration, context);
        var serviceInformerEventSource = new InformerEventSource<>(serviceInformerConfiguration, context);
        var ingressInformerEventSource = new InformerEventSource<>(ingressInformerConfiguration, context);

        return Map.of(
                PVC_EVENT_SOURCE, pcvInformerEventSource,
                DEPLOYMENT_EVENT_SOURCE, deploymentInformerEventSource,
                SERVICE_EVENT_SOURCE, serviceInformerEventSource,
                INGRESS_EVENT_SOURCE, ingressInformerEventSource
        );
    }
}
