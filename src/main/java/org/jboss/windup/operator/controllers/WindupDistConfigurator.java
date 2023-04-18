package org.jboss.windup.operator.controllers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.quarkus.logging.Log;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.cdrs.v2alpha1.DBSecret;
import org.jboss.windup.operator.cdrs.v2alpha1.WebConsolePersistentVolumeClaim;
import org.jboss.windup.operator.cdrs.v2alpha1.Windup;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WindupDistConfigurator {

    private final Windup cr;

    private final List<EnvVar> allEnvVars;
    private final List<Volume> allVolumes;
    private final List<VolumeMount> allVolumeMounts;

    public WindupDistConfigurator(Windup cr) {
        this.cr = cr;
        this.allEnvVars = new ArrayList<>();
        this.allVolumes = new ArrayList<>();
        this.allVolumeMounts = new ArrayList<>();

        configureDefaults();
        configureDatabase();
        configureDataDirectory();
        configureSSO();
        configureJGroups();
    }

    public List<EnvVar> getAllEnvVars() {
        return allEnvVars;
    }

    public List<Volume> getAllVolumes() {
        return allVolumes;
    }

    public List<VolumeMount> getAllVolumeMounts() {
        return allVolumeMounts;
    }

    private void configureDefaults() {
        List<EnvVar> envVars = optionMapper(cr.getSpec())
                // Mandatory additional ENV
                .mapOption("IS_MASTER", spec -> "true")
                .mapOption("MESSAGING_SERIALIZER", spec -> "http.post.serializer")
                .mapOption("AUTO_DEPLOY_EXPLODED", spec -> "false")
                .mapOption("GC_MAX_METASPACE_SIZE", spec -> "512")
                .mapOption("MAX_POST_SIZE", spec -> "4294967296")
                .mapOption("SSO_DISABLE_SSL_CERTIFICATE_VALIDATION", spec -> "true")
                .mapOption("SSO_FORCE_LEGACY_SECURITY", spec -> "false")
                // Optional additional ENV
                .mapOption("OPENSHIFT_KUBE_PING_LABELS", spec -> "application=" + cr.getMetadata().getName())
                .mapOption("OPENSHIFT_KUBE_PING_NAMESPACE", spec -> cr.getMetadata().getNamespace())
                .getEnvVars();

        allEnvVars.addAll(envVars);
    }

    private void configureDatabase() {
        String dbSecretName = DBSecret.getSecretName(cr);

        List<EnvVar> envVars = optionMapper(cr.getSpec())
                .mapOption("DB_USERNAME", spec -> new SecretKeySelector(Constants.DB_SECRET_USERNAME, dbSecretName, false))
                .mapOption("DB_PASSWORD", spec -> new SecretKeySelector(Constants.DB_SECRET_PASSWORD, dbSecretName, false))
                .mapOption("DB_DATABASE", spec -> new SecretKeySelector(Constants.DB_SECRET_DATABASE_NAME, dbSecretName, false))
                .getEnvVars();

        allEnvVars.addAll(envVars);
    }

    private void configureDataDirectory() {
        var volume1 = new VolumeBuilder()
                .withName("windup-web-pvol")
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(WebConsolePersistentVolumeClaim.getPersistentVolumeClaimName(cr))
                        .build()
                )
                .build();
        var volume2 = new VolumeBuilder()
                .withName("windup-web-pvol-data")
                .withNewEmptyDir()
                .endEmptyDir()
                .build();

        var volumeMount1 = new VolumeMountBuilder()
                .withName(volume1.getName())
                .withMountPath("/opt/windup/data/windup")
                .build();
        var volumeMount2 = new VolumeMountBuilder()
                .withName(volume2.getName())
                .withMountPath("/opt/windup/data")
                .build();

        allVolumes.add(volume1);
        allVolumes.add(volume2);

        allVolumeMounts.add(volumeMount1);
        allVolumeMounts.add(volumeMount2);
    }

    private void configureSSO() {
        List<EnvVar> envVars = optionMapper(cr.getSpec().getSsoSpec())
                .mapOption("SSO_AUTH_SERVER_URL", WindupSpec.SSOSpec::getServerUrl)
                .mapOption("SSO_REALM", WindupSpec.SSOSpec::getRealm)
                .mapOption("SSO_SSL_REQUIRED", WindupSpec.SSOSpec::getSslRequired)
                .mapOption("SSO_CLIENT_ID", WindupSpec.SSOSpec::getClientId)
                .getEnvVars();

        allEnvVars.addAll(envVars);
    }

    private void configureJGroups() {
        List<EnvVar> envVars = optionMapper(cr.getSpec().getJgroupsSpec())
                .mapOption("JGROUPS_ENCRYPT_SECRET", WindupSpec.JGroupsSpec::getEncryptSecret)
                .mapOption("JGROUPS_ENCRYPT_KEYSTORE_DIR", "/etc/jgroups-encrypt-secret-volume")
                .mapOption("JGROUPS_ENCRYPT_KEYSTORE", WindupSpec.JGroupsSpec::getEncryptKeystore)
                .mapOption("JGROUPS_ENCRYPT_NAME", WindupSpec.JGroupsSpec::getEncryptName)
                .mapOption("JGROUPS_ENCRYPT_PASSWORD", WindupSpec.JGroupsSpec::getEncryptPassword)
                .mapOption("JGROUPS_CLUSTER_PASSWORD", WindupSpec.JGroupsSpec::getClusterPassword)
                .getEnvVars();

        allEnvVars.addAll(envVars);
    }

    private <T> OptionMapper<T> optionMapper(T optionSpec) {
        return new OptionMapper<>(optionSpec);
    }

    private class OptionMapper<T> {
        private final T categorySpec;
        private final List<EnvVar> envVars;

        public OptionMapper(T optionSpec) {
            this.categorySpec = optionSpec;
            this.envVars = new ArrayList<>();
        }

        public List<EnvVar> getEnvVars() {
            return envVars;
        }

        public <R> OptionMapper<T> mapOption(String optionName, Function<T, R> optionValueSupplier) {
            if (categorySpec == null) {
                Log.debugf("No category spec provided for %s", optionName);
                return this;
            }

            R value = optionValueSupplier.apply(categorySpec);

            if (value == null || value.toString().trim().isEmpty()) {
                Log.debugf("No value provided for %s", optionName);
                return this;
            }

            EnvVarBuilder envVarBuilder = new EnvVarBuilder()
                    .withName(optionName);

            if (value instanceof SecretKeySelector) {
                envVarBuilder.withValueFrom(new EnvVarSourceBuilder().withSecretKeyRef((SecretKeySelector) value).build());
            } else {
                envVarBuilder.withValue(String.valueOf(value));
            }

            envVars.add(envVarBuilder.build());

            return this;
        }

        public <R> OptionMapper<T> mapOption(String optionName) {
            return mapOption(optionName, s -> null);
        }

        public <R> OptionMapper<T> mapOption(String optionName, R optionValue) {
            return mapOption(optionName, s -> optionValue);
        }

        protected <R extends Collection<?>> OptionMapper<T> mapOptionFromCollection(String optionName, Function<T, R> optionValueSupplier) {
            return mapOption(optionName, s -> {
                var value = optionValueSupplier.apply(s);
                if (value == null) return null;
                return value.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
            });
        }
    }

}
