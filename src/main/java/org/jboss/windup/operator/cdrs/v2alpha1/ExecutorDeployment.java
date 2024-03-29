package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ExecActionBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LifecycleBuilder;
import io.fabric8.kubernetes.api.model.LifecycleHandlerBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.jboss.windup.operator.Config;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@KubernetesDependent(labelSelector = ExecutorDeployment.LABEL_SELECTOR)
@ApplicationScoped
public class ExecutorDeployment extends CRUDKubernetesDependentResource<Deployment, Windup> implements Matcher<Deployment, Windup> {

    public static final String LABEL_SELECTOR="app.kubernetes.io/managed-by=windup-operator,component=executor";

    @Inject
    Config config;

    public ExecutorDeployment() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(Windup cr, Context<Windup> context) {
        return newDeployment(cr, context);
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

    @SuppressWarnings("unchecked")
    private Deployment newDeployment(Windup cr, Context<Windup> context) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(getDeploymentName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(contextLabels)
                .addToLabels("component", "executor")
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .withSpec(getDeploymentSpec(cr, context))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DeploymentSpec getDeploymentSpec(Windup cr, Context<Windup> context) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        Map<String, String> selectorLabels = Constants.DB_SELECTOR_LABELS;
        String image = config.executorImage();
        String imagePullPolicy = config.imagePullPolicy();

        WindupSpec.ResourcesLimitSpec resourcesLimitSpec = CRDUtils.getValueFromSubSpec(cr.getSpec(), WindupSpec::getExecutorResourceLimitSpec)
                .orElse(null);

        return new DeploymentSpecBuilder()
                .withStrategy(new DeploymentStrategyBuilder()
                        .withType("Recreate")
                        .build()
                )
                .withReplicas(cr.getSpec().getExecutorInstances())
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
                                .withTerminationGracePeriodSeconds(75L)
                                .withImagePullSecrets(cr.getSpec().getImagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.WINDUP_EXECUTOR_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(getEnvVars(cr, config))
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/bash", "-c", "/opt/windup-cli/bin/livenessProbe.sh")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(120)
                                                .withTimeoutSeconds(10)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/bash", "-c", "/opt/windup-cli/bin/livenessProbe.sh")
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
                                                                .withCommand("/opt/windup-cli/bin/stop.sh")
                                                                .build()
                                                        )
                                                        .build()
                                                )
                                                .build()
                                        )
                                        .withVolumeMounts(new VolumeMountBuilder()
                                                .withName("executor-pvol")
                                                .withMountPath("/opt/windup/data")
                                                .build()
                                        )
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
                                .withVolumes(new VolumeBuilder()
                                        .withName("executor-pvol")
                                        .withNewEmptyDir()
                                        .endEmptyDir()
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build();
    }

    private List<EnvVar> getEnvVars(Windup cr, Config config) {
        return Arrays.asList(
                new EnvVarBuilder()
                        .withName("IS_MASTER")
                        .withValue("false")
                        .build(),
                new EnvVarBuilder()
                        .withName("MESSAGING_SERIALIZER")
                        .withValue("http.post.serializer")
                        .build(),
                new EnvVarBuilder()
                        .withName("MESSAGING_USER")
                        .withValue("jms-user")
                        .build(),
                new EnvVarBuilder()
                        .withName("MESSAGING_PASSWORD")
                        .withValue("gthudfal")
                        .build(),
                new EnvVarBuilder()
                        .withName("MESSAGING_HOST_VAR")
                        .withValue(WebService.getServiceName(cr) + "_SERVICE_HOST")
                        .build()

        );
    }

    public static String getDeploymentName(Windup cr) {
        return cr.getMetadata().getName() + Constants.EXECUTOR_DEPLOYMENT_SUFFIX;
    }
}
