package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ExecActionBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LifecycleBuilder;
import io.fabric8.kubernetes.api.model.LifecycleHandlerBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.jboss.windup.operator.AppServerConfig;
import org.jboss.windup.operator.Config;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.controllers.WindupDistConfigurator;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@KubernetesDependent(resourceDiscriminator = WebDeploymentDiscriminator.class)
@ApplicationScoped
public class WebDeployment extends CRUDKubernetesDependentResource<Deployment, Windup>
        implements Matcher<Deployment, Windup>, Condition<Deployment, Windup> {

    @Inject
    AppServerConfig appServerConfig;

    public WebDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Windup cr, Context<Windup> context) {
        WindupDistConfigurator distConfigurator = new WindupDistConfigurator(cr);
        return newDeployment(cr, context, distConfigurator);
    }

    @Override
    public Result<Deployment> match(Deployment actual, Windup cr, Context<Windup> context) {
        final var container = actual.getSpec()
                .getTemplate().getSpec().getContainers()
                .stream()
                .findFirst();

        return Result.nonComputed(container
                .map(c -> c.getImage() != null)
                .orElse(false)
        );
    }

    @Override
    public boolean isMet(Windup primary, Deployment secondary, Context<Windup> context) {
        return context.getSecondaryResource(Deployment.class, new WebDeploymentDiscriminator())
                .map(deployment -> {
                    final var status = deployment.getStatus();
                    if (status != null) {
                        final var readyReplicas = status.getReadyReplicas();
                        return readyReplicas != null && readyReplicas >= 1;
                    }
                    return false;
                })
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    private Deployment newDeployment(Windup cr, Context<Windup> context, WindupDistConfigurator distConfigurator) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(getDeploymentName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(contextLabels)
                .withAnnotations(Map.of(
                        "app.openshift.io/connects-to", "[" +
                                "{\"apiVersion\":\"apps/v1\",\"kind\":\"Deployment\",\"name\":\"" + DBDeployment.getDeploymentName(cr) + "\"}," +
                                "{\"apiVersion\":\"apps/v1\",\"kind\":\"Deployment\",\"name\":\"" + ExecutorDeployment.getDeploymentName(cr) + "\"}]"
                ))
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getDeploymentSpec(cr, context, distConfigurator))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DeploymentSpec getDeploymentSpec(Windup cr, Context<Windup> context, WindupDistConfigurator distConfigurator) {
        final var config = (Config) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_CONFIG_KEY, Config.class);
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        Map<String, String> selectorLabels = Constants.WEB_SELECTOR_LABELS;
        String image = config.windup().webImage();
        String imagePullPolicy = config.windup().imagePullPolicy();

        List<EnvVar> envVars = distConfigurator.getAllEnvVars();
        List<Volume> volumes = distConfigurator.getAllVolumes();
        List<VolumeMount> volumeMounts = distConfigurator.getAllVolumeMounts();

        WindupSpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), WindupSpec::getWebResourceLimitSpec)
                .orElse(null);

        return new DeploymentSpecBuilder()
                .withStrategy(new DeploymentStrategyBuilder()
                        .withType("Recreate")
                        .build()
                )
                .withReplicas(1)
                .withSelector(new LabelSelectorBuilder()
                        .withMatchLabels(selectorLabels)
                        .build()
                )
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata()
                        .withLabels(Stream
                                .concat(contextLabels.entrySet().stream(), selectorLabels.entrySet().stream())
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                        )
                        .endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withRestartPolicy("Always")
                                .withTerminationGracePeriodSeconds(70L)
                                .withImagePullSecrets(cr.getSpec().getImagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.WINDUP_WEB_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(envVars)
                                        .withPorts(
                                                new ContainerPortBuilder()
                                                        .withName("http")
                                                        .withProtocol(Constants.SERVICE_PROTOCOL)
                                                        .withContainerPort(8080)
                                                        .build(),
                                                new ContainerPortBuilder()
                                                        .withName("jolokia")
                                                        .withProtocol(Constants.SERVICE_PROTOCOL)
                                                        .withContainerPort(8778)
                                                        .build(),
                                                new ContainerPortBuilder()
                                                        .withName("ping")
                                                        .withProtocol(Constants.SERVICE_PROTOCOL)
                                                        .withContainerPort(8888)
                                                        .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand(appServerConfig.getWebReadinessProbeCmd())
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(120)
                                                .withTimeoutSeconds(10)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand(appServerConfig.getWebLivenessProbeCmd())
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(120)
                                                .withTimeoutSeconds(10)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withLifecycle(new LifecycleBuilder()
                                                .withPreStop(new LifecycleHandlerBuilder()
                                                        .withExec(new ExecActionBuilder()
                                                                .withCommand("${JBOSS_HOME}/bin/jboss-cli.sh", "-c", ":shutdown(timeout=60)")
                                                                .build()
                                                        )
                                                        .build()
                                                )
                                                .build()
                                        )
                                        .withVolumeMounts(volumeMounts)
                                        .withResources(new ResourceRequirementsBuilder()
                                                .withRequests(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, WindupSpec.ResourcesLimitSpec::getCpuRequest).orElse("0.5")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, WindupSpec.ResourcesLimitSpec::getMemoryRequest).orElse("0.5Gi"))
                                                ))
                                                .withLimits(Map.of(
                                                        "cpu", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, WindupSpec.ResourcesLimitSpec::getCpuLimit).orElse("4")),
                                                        "memory", new Quantity(CRDUtils.getValueFromSubSpec(resourcesLimitSpec, WindupSpec.ResourcesLimitSpec::getMemoryLimit).orElse("4Gi"))
                                                ))
                                                .build()
                                        )
                                        .build()
                                )
                                .withVolumes(volumes)
                                .build()
                        )
                        .build()
                )
                .build();
    }

    public static String getDeploymentName(Windup cr) {
        return cr.getMetadata().getName() + Constants.WEB_DEPLOYMENT_SUFFIX;
    }

}
