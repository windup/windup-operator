/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.ExecActionBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import org.jboss.windup.operator.Config;
import org.jboss.windup.operator.Constants;

import javax.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@KubernetesDependent(resourceDiscriminator = DBDeploymentDiscriminator.class)
@ApplicationScoped
public class DBDeployment extends CRUDKubernetesDependentResource<Deployment, Windup>
        implements Matcher<Deployment, Windup>, Condition<Deployment, Windup> {

    public DBDeployment() {
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

    @Override
    public boolean isMet(Windup primary, Deployment secondary, Context<Windup> context) {
        return context.getSecondaryResource(Deployment.class, new DBDeploymentDiscriminator())
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
    private Deployment newDeployment(Windup cr, Context<Windup> context) {
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(getDeploymentName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(contextLabels)
                .endMetadata()
                .withSpec(getDeploymentSpec(cr, context))
                .build();
    }

    @SuppressWarnings("unchecked")
    private DeploymentSpec getDeploymentSpec(Windup cr, Context<Windup> context) {
        final var config = (Config) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_CONFIG_KEY, Config.class);
        final var contextLabels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        Map<String, String> selectorLabels = Constants.DB_SELECTOR_LABELS;
        String image = config.windup().dbImage();
        String imagePullPolicy = config.windup().imagePullPolicy();

        return new DeploymentSpecBuilder()
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
                                .withTerminationGracePeriodSeconds(30L)
                                .withImagePullSecrets(cr.getSpec().getImagePullSecrets())
                                .withContainers(new ContainerBuilder()
                                        .withName(Constants.WINDUP_DB_NAME)
                                        .withImage(image)
                                        .withImagePullPolicy(imagePullPolicy)
                                        .withEnv(getEnvVars(cr, config))
                                        .withPorts(new ContainerPortBuilder()
                                                .withName("tcp")
                                                .withProtocol("TCP")
                                                .withContainerPort(5432)
                                                .build()
                                        )
                                        .withLivenessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/sh", "-c", "psql -U $POSTGRESQL_USER -d $POSTGRESQL_DATABASE -c 'SELECT 1'")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(60)
                                                .withTimeoutSeconds(10)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withReadinessProbe(new ProbeBuilder()
                                                .withExec(new ExecActionBuilder()
                                                        .withCommand("/bin/sh", "-c", "psql -U $POSTGRESQL_USER -d $POSTGRESQL_DATABASE -c 'SELECT 1'")
                                                        .build()
                                                )
                                                .withInitialDelaySeconds(5)
                                                .withTimeoutSeconds(1)
                                                .withPeriodSeconds(10)
                                                .withSuccessThreshold(1)
                                                .withFailureThreshold(3)
                                                .build()
                                        )
                                        .withVolumeMounts(new VolumeMountBuilder()
                                                .withName("db-pvol")
                                                .withMountPath("/var/lib/pgsql/data")
                                                .build()
                                        )
                                        .build()
                                )
                                .withVolumes(new VolumeBuilder()
                                        .withName("db-pvol")
                                        .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                                                .withClaimName(DBPersistentVolumeClaim.getPersistentVolumeClaimName(cr))
                                                .build()
                                        )
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
                        .withName("POSTGRESQL_MAX_CONNECTIONS")
                        .withValue("200")
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_MAX_PREPARED_TRANSACTIONS")
                        .withValue("200")
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_USER")
                        .withNewValueFrom()
                        .withNewSecretKeyRef()
                        .withName(DBSecret.getSecretName(cr))
                        .withKey(Constants.DB_SECRET_USERNAME)
                        .withOptional(false)
                        .endSecretKeyRef()
                        .endValueFrom()
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_PASSWORD")
                        .withNewValueFrom()
                        .withNewSecretKeyRef()
                        .withName(DBSecret.getSecretName(cr))
                        .withKey(Constants.DB_SECRET_PASSWORD)
                        .withOptional(false)
                        .endSecretKeyRef()
                        .endValueFrom()
                        .build(),
                new EnvVarBuilder()
                        .withName("POSTGRESQL_DATABASE")
                        .withNewValueFrom()
                        .withNewSecretKeyRef()
                        .withName(DBSecret.getSecretName(cr))
                        .withKey(Constants.DB_SECRET_DATABASE_NAME)
                        .withOptional(false)
                        .endSecretKeyRef()
                        .endValueFrom()
                        .build()
        );
    }

    public static String getDeploymentName(Windup cr) {
        return cr.getMetadata().getName() + Constants.DB_DEPLOYMENT_SUFFIX;
    }
}
