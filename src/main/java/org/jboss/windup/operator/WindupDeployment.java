package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.QuantityBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressTLSBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.windup.operator.model.WindupResource;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WindupDeployment {
    private static final Logger LOG = Logger.getLogger(WindupDeployment.class);
    private static final String NAMESPACE = "default";

    @Inject
    KubernetesClient k8sClient;

    public void deployWindup(WindupResource windupResource) {
        List<Deployment> deployments = createDeployment(windupResource);
        k8sClient.apps().deployments().inNamespace(NAMESPACE).createOrReplace(deployments.get(0));
        k8sClient.apps().deployments().inNamespace(NAMESPACE).createOrReplace(deployments.get(1));
        k8sClient.apps().deployments().inNamespace(NAMESPACE).createOrReplace(deployments.get(2));

        List<Service> services = createServices(windupResource);
        k8sClient.services().inNamespace(NAMESPACE).createOrReplace(services.get(0));
        k8sClient.services().inNamespace(NAMESPACE).createOrReplace(services.get(1));
        k8sClient.services().inNamespace(NAMESPACE).createOrReplace(services.get(2));

        List<Ingress> ingresses = createIngresses(windupResource);
        k8sClient.network().ingress().inNamespace(NAMESPACE).createOrReplace(ingresses.get(0));
        k8sClient.network().ingress().inNamespace(NAMESPACE).createOrReplace(ingresses.get(1));

        List<PersistentVolumeClaim> volumes = createVolumes(windupResource);
        k8sClient.persistentVolumeClaims().createOrReplace(volumes.get(0));
        k8sClient.persistentVolumeClaims().createOrReplace(volumes.get(1));

        // ContainerBuilder , PodSpecBuilder , PodBuilder
    }

    private List<Service> createServices(WindupResource windupResource) {
        Service mtaWebConsoleSvc = k8sClient.services().createNew()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName(windupResource.getSpec().getApplication_name())
          .withLabels(Map.of("application", windupResource.getSpec().getApplication_name(), "app", windupResource.getSpec().getApplication_name()))
          .withAnnotations(Map.of("description", "The web server's http port", "service.alpha.openshift.io/dependencies", "[{\"name\": \"" + windupResource.getSpec().getApplication_name() + "-postgresql\", \"kind\": \"Service\"}]"))
        .endMetadata()
        .withNewSpec()
          .withPorts(Collections.singletonList(new ServicePort("a", "name", 0, 8080, "", new IntOrString(8080) )))
          .withSelector(Collections.singletonMap("deploymentConfig", windupResource.getSpec().getApplication_name()))
        .endSpec().done();

        Service postgreSvc = k8sClient.services().createNew()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName(windupResource.getSpec().getApplication_name() + "-postgresql")
          .withLabels(Map.of("application", windupResource.getSpec().getApplication_name(), "app", windupResource.getSpec().getApplication_name()))
          .withAnnotations(Map.of("description", "The web server's http port", "service.alpha.openshift.io/dependencies", "[{\"name\": \"" + windupResource.getSpec().getApplication_name() + "-postgresql\", \"kind\": \"Service\"}]"))
        .endMetadata()
        .withNewSpec()
          .withPorts(new ServicePort("a", "name", 0, 5432, "", new IntOrString(5432) ))
          .withSelector(Collections.singletonMap("deploymentConfig",windupResource.getSpec().getApplication_name() + "-postgresql"))
        .endSpec().done();

        Service amqSvc = k8sClient.services().createNew()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName(windupResource.getSpec().getApplication_name() + "-amq")
          .withLabels(Map.of("application",windupResource.getSpec().getApplication_name() + "-amq"))
          .withAnnotations(Map.of("description", "MTA Master AMQ port."))
        .and()
        .withNewSpec()
          .withPorts(new ServicePort("a", "name", 0, 61616, "", new IntOrString(61616) ))
          .withSelector(Collections.singletonMap("deploymentConfig", windupResource.getSpec().getApplication_name()))
        .and().done();

        return List.of(mtaWebConsoleSvc, postgreSvc, postgreSvc);
    }

    private List<Ingress> createIngresses(WindupResource windupResource) {
        Ingress ingressWebConsole = new IngressBuilder()
          .withNewMetadata()
             .withName(windupResource.getSpec().getApplication_name())
             .addToLabels("application", windupResource.getSpec().getApplication_name())
             .addToAnnotations("description", "Route for application's http service.")
             .addToAnnotations("console.alpha.openshift.io/overview-app-route","true")
          .endMetadata()
          .withNewSpec()
            .addToTls(new IngressTLSBuilder().withHosts(windupResource.getSpec().getHostname_http()).build())
            .addNewRule()
                .withHost(windupResource.getSpec().getHostname_http())
              .withNewHttp()
                .addNewPath()
                  .withPath("/").withNewBackend().withServiceName(windupResource.getSpec().getApplication_name()).withServicePort(new IntOrString(80)).endBackend()
                .endPath()
              .endHttp()
            .endRule()
          .endSpec()
        .build();

        return List.of(ingressWebConsole);
    }

    private List<PersistentVolumeClaim> createVolumes(WindupResource windupResource) {
        PersistentVolumeClaim postgrPersistentVolumeClaim = new PersistentVolumeClaimBuilder()
        .withNewMetadata()
          .withName(windupResource.getSpec().getApplication_name() + "-postgresql-claim")
          .addToLabels("application", windupResource.getSpec().getApplication_name())
        .endMetadata()
        .withNewSpec()
            .withAccessModes("ReadWriteOne")
            .withNewResources().addToRequests("storage", new Quantity(windupResource.getSpec().getVolumeCapacity())).endResources()
        .endSpec()
        .build();

        PersistentVolumeClaim mtaPersistentVolumeClaim = new PersistentVolumeClaimBuilder()
        .withNewMetadata().withName(windupResource.getSpec().getApplication_name() + "-mta-web-claim").addToLabels("application", windupResource.getSpec().getApplication_name()).endMetadata()
        .withNewSpec()
            .withAccessModes("ReadWriteMany")
            .withNewResources().addToRequests("storage", new Quantity(windupResource.getSpec().getMta_Volume_Capacity())).endResources()
        .endSpec()
        .build();
        return List.of(postgrPersistentVolumeClaim, mtaPersistentVolumeClaim);
    }

    private List<Deployment> createDeployment(WindupResource windupResource) {
        Deployment deploymentMTAweb = new DeploymentBuilder()
            .withNewMetadata()
               .withName(windupResource.getSpec().getApplication_name())
               .addToLabels("application", windupResource.getSpec().getApplication_name())
            .endMetadata()
            .withNewSpec()
               .withReplicas(1)
               .withNewSelector()
                   .addToMatchLabels("deploymentConfig", windupResource.getSpec().getApplication_name())
               .endSelector()
               .withNewStrategy()
                  .withType("Recreate")
                .endStrategy()
               .withNewTemplate()
                   .withNewMetadata()
                      .addToLabels("deploymentConfig", windupResource.getSpec().getApplication_name())
                      .addToLabels("application", windupResource.getSpec().getApplication_name())
                      .withName(windupResource.getSpec().getApplication_name())
                   .endMetadata()
                   .withNewSpec()
                      .withTerminationGracePeriodSeconds(75L)
                      .addNewContainer()
                          .withName(windupResource.getSpec().getApplication_name())
                          .withImage(windupResource.getSpec().getContainer_repository() + "/" + windupResource.getSpec().getDocker_images_user() + "/windup-web-openshift:" + windupResource.getSpec().getDocker_images_tag())
                          .withNewImagePullPolicy("Always")
                          .withNewResources()
                             .addToRequests(Map.of("cpu", new QuantityBuilder().withAmount(windupResource.getSpec().getWeb_cpu_request()).build()))
                             .addToRequests(Map.of("memory", new QuantityBuilder().withAmount(windupResource.getSpec().getWeb_mem_request()).build()))
                             .addToLimits(Map.of("cpu", new QuantityBuilder().withAmount(windupResource.getSpec().getWeb_cpu_limit()).build()))
                             .addToLimits(Map.of("memory", new QuantityBuilder().withAmount(windupResource.getSpec().getWeb_mem_limit()).build()))
                          .endResources()
                          .withVolumeMounts(List.of(
                                new VolumeMountBuilder().withName(windupResource.getSpec().getApplication_name() + "-mta-web-pvol").withMountPath("/opt/eap/standalone/data/windup").withReadOnly(false).build(),
                                new VolumeMountBuilder().withName(windupResource.getSpec().getApplication_name() + "-mta-web-pvol-data").withMountPath("/opt/eap/standalone/data").withReadOnly(false).build()
                                ))
                          .withNewLifecycle()
                             .withNewPreStop()
                               .withNewExec()
                                 .withCommand("/opt/eap/bin/jboss-cli.sh","-c",":shutdown(timeout=60)")
                               .endExec()
                             .endPreStop()
                          .endLifecycle()
                          .withNewLivenessProbe()
                            .withNewExec()
                                .withCommand("/bin/bash","-c","/opt/eap/bin/livenessProbe.sh")
                            .endExec()
                            .withInitialDelaySeconds(Integer.parseInt(windupResource.getSpec().getWebLivenessInitialDelaySeconds()))
                            .withFailureThreshold(3)
                            .withSuccessThreshold(1)
                            .withTimeoutSeconds(Integer.parseInt(windupResource.getSpec().getWebLivenessTimeoutSeconds()))
                          .endLivenessProbe()
                          .withNewReadinessProbe()
                            .withNewExec()
                                .withCommand("/bin/bash","-c","/opt/eap/bin/readinessProbe.sh")
                            .endExec()
                            .withInitialDelaySeconds(Integer.parseInt(windupResource.getSpec().getWebReadinessInitialDelaySeconds()))
                            .withFailureThreshold(3)
                            .withSuccessThreshold(1)
                            .withTimeoutSeconds(Integer.parseInt(windupResource.getSpec().getWebReadinessTimeoutSeconds()))
                          .endReadinessProbe()
                          .addNewPort().withName("jolokia").withContainerPort(8778).withProtocol("TCP").endPort()
                          .addNewPort().withName("http").withContainerPort(8080).withProtocol("TCP").endPort()
                          .addNewPort().withName("ping").withContainerPort(8888).withProtocol("TCP").endPort()
                          .addNewEnv().withName("IS_MASTER").withValue("true").endEnv()
                        .addNewEnv().withName("MESSAGING_SERIALIZER").withValue(windupResource.getSpec().getMessaging_serializer()).endEnv()
                        .addNewEnv().withName("GRAPH_BASE_OUTPUT_PATH").withValue("/opt/eap/standalone/data/windup-graphs").endEnv()
                        .addNewEnv().withName("DB_SERVICE_PREFIX_MAPPING").withValue(windupResource.getSpec().getApplication_name() + "-postgresql=DB").endEnv()
                        .addNewEnv().withName("DB_JNDI").withValue(windupResource.getSpec().getDb_jndi()).endEnv()
                        .addNewEnv().withName("DB_USERNAME").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getDb_username(), "user" + RandomStringUtils.randomAlphanumeric(3))).endEnv()
                        .addNewEnv().withName("DB_PASSWORD").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getDb_password(),RandomStringUtils.randomAlphanumeric(8)) ).endEnv()
                        .addNewEnv().withName("DB_DATABASE").withValue(windupResource.getSpec().getDb_username()).endEnv()
                        .addNewEnv().withName("TX_DATABASE_PREFIX_MAPPING").withValue(windupResource.getSpec().getApplication_name() + "-postgresql=DB").endEnv()
                        .addNewEnv().withName("DB_MIN_POOL_SIZE").withValue(windupResource.getSpec().getDb_min_pool_size()).endEnv()
                        .addNewEnv().withName("DB_MAX_POOL_SIZE").withValue(windupResource.getSpec().getDb_max_pool_size()).endEnv()
                        .addNewEnv().withName("DB_TX_ISOLATION").withValue(windupResource.getSpec().getDb_tx_isolation()).endEnv()
                        .addNewEnv().withName("OPENSHIFT_KUBE_PING_LABELS").withValue("application=" + windupResource.getSpec().getApplication_name()).endEnv()
                        .addNewEnv().withName("OPENSHIFT_KUBE_PING_NAMESPACE").withNewValueFrom().withNewFieldRef("apiVersion", "metadata.namespace").endValueFrom().endEnv()
                        .addNewEnv().withName("HTTPS_KEYSTORE_DIR").withValue("/etc/eap-secret-volume").endEnv()
                        .addNewEnv().withName("MQ_CLUSTER_PASSWORD").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getMq_cluster_password(), RandomStringUtils.randomAlphanumeric(8))).endEnv()
                        .addNewEnv().withName("MQ_QUEUES").withValue(windupResource.getSpec().getMq_queues()).endEnv()
                        .addNewEnv().withName("MQ_TOPICS").withValue(windupResource.getSpec().getMq_topics()).endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_SECRET").withValue(windupResource.getSpec().getJgroups_encrypt_secret()).endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE_DIR").withValue("/etc/jgroups-encrypt-secret-volume").endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE").withValue(windupResource.getSpec().getJgroups_encrypt_keystore()).endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_NAME").withValue(windupResource.getSpec().getJgroups_encrypt_name()).endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_PASSWORD").withValue(windupResource.getSpec().getJgroups_encrypt_password()).endEnv()
                        .addNewEnv().withName("JGROUPS_CLUSTER_PASSWORD").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getJgroups_cluster_password(),RandomStringUtils.randomAlphanumeric(8))).endEnv()
                        .addNewEnv().withName("AUTO_DEPLOY_EXPLODED").withValue(windupResource.getSpec().getAuto_deploy_exploded()).endEnv()
                        .addNewEnv().withName("DEFAULT_JOB_REPOSITORY").withValue(windupResource.getSpec().getApplication_name() + "-postgresql").endEnv()
                        .addNewEnv().withName("TIMER_SERVICE_DATA_STORE").withValue(windupResource.getSpec().getApplication_name() + "-postgresql").endEnv()
                        .addNewEnv().withName("SSO_URL").withValue(windupResource.getSpec().getSso_url()).endEnv()
                        .addNewEnv().withName("SSO_SERVICE_URL").withValue(windupResource.getSpec().getSso_service_url()).endEnv()
                        .addNewEnv().withName("SSO_REALM").withValue(windupResource.getSpec().getSso_realm()).endEnv()
                        .addNewEnv().withName("SSO_USERNAME").withValue(windupResource.getSpec().getSso_username()).endEnv()
                        .addNewEnv().withName("SSO_PASSWORD").withValue(windupResource.getSpec().getSso_password()).endEnv()
                        .addNewEnv().withName("SSO_PUBLIC_KEY").withValue(windupResource.getSpec().getSso_public_key()).endEnv()
                        .addNewEnv().withName("SSO_BEARER_ONLY").withValue(windupResource.getSpec().getSso_bearer_only()).endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE_SECRET").withValue(windupResource.getSpec().getSso_saml_keystore_secret()).endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE").withValue(windupResource.getSpec().getSso_saml_keystore()).endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE_DIR").withValue("/etc/sso-saml-secret-volume").endEnv()
                        .addNewEnv().withName("SSO_SAML_CERTIFICATE_NAME").withValue(windupResource.getSpec().getSso_saml_certificate_name()).endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE_PASSWORD").withValue(windupResource.getSpec().getSso_saml_keystore_password()).endEnv()
                        .addNewEnv().withName("SSO_SECRET").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getSso_secret(), RandomStringUtils.randomAlphanumeric(8))).endEnv()
                        .addNewEnv().withName("SSO_ENABLE_CORS").withValue(windupResource.getSpec().getSso_enable_cors()).endEnv()
                        .addNewEnv().withName("SSO_SAML_LOGOUT_PAGE").withValue(windupResource.getSpec().getSso_saml_logout_page()).endEnv()
                        .addNewEnv().withName("SSO_DISABLE_SSL_CERTIFICATE_VALIDATION").withValue(windupResource.getSpec().getSso_disable_ssl_certificate_validation()).endEnv()
                        .addNewEnv().withName("SSO_TRUSTSTORE").withValue(windupResource.getSpec().getSso_truststore()).endEnv()
                        .addNewEnv().withName("SSO_TRUSTSTORE_DIR").withValue("/etc/sso-secret-volume").endEnv()
                        .addNewEnv().withName("SSO_TRUSTSTORE_PASSWORD").withValue(windupResource.getSpec().getSso_truststore_password()).endEnv()
                        .addNewEnv().withName("GC_MAX_METASPACE_SIZE").withValue(windupResource.getSpec().getGc_max_metaspace_size()).endEnv()
                        .addNewEnv().withName("MAX_POST_SIZE").withValue(windupResource.getSpec().getMax_post_size()).endEnv()
                      .endContainer()
                      .addNewVolume().withName(windupResource.getSpec().getApplication_name() + "-mta-web-pvol").withNewPersistentVolumeClaim().withClaimName(windupResource.getSpec().getApplication_name() + "-mta-web-claim").endPersistentVolumeClaim().endVolume()
                      .addNewVolume().withName(windupResource.getSpec().getApplication_name() + "-mta-web-pvol-data").withNewEmptyDir().endEmptyDir().endVolume()
                   .endSpec()
               .endTemplate()
            .endSpec()
            .build();

        Deployment deploymentExecutor = new DeploymentBuilder()
            .withNewMetadata()
               .withName(windupResource.getSpec().getApplication_name() + "-executor")
               .addToLabels("application",windupResource.getSpec().getApplication_name() + "-executor")
            .endMetadata()
            .withNewSpec()
               .withReplicas(1)
               .withNewSelector()
                   .addToMatchLabels("deploymentConfig",windupResource.getSpec().getApplication_name() + "-executor")
               .endSelector()
               .withNewStrategy()
                  .withType("Recreate")
                .endStrategy()
               .withNewTemplate()
                   .withNewMetadata()
                      .addToLabels("deploymentConfig",windupResource.getSpec().getApplication_name() + "-executor")
                      .addToLabels("application",windupResource.getSpec().getApplication_name() + "-executor")
                      .withName(windupResource.getSpec().getApplication_name() + "-executor")
                   .endMetadata()
                   .withNewSpec()
                      .withTerminationGracePeriodSeconds(75L)
                      .addNewContainer()
                          .withName(windupResource.getSpec().getApplication_name() + "-executor")
                          .withImage(windupResource.getSpec().getContainer_repository() + "/" + windupResource.getSpec().getDocker_images_user() + "/windup-web-openshift-messaging-executor:" + windupResource.getSpec().getDocker_images_tag())
                          .addNewPort().withName("postgre").withContainerPort(8778).withProtocol("TCP").endPort()
                          .withNewImagePullPolicy("Always")
                          .withNewResources()
                            .addToRequests(Map.of("cpu", new QuantityBuilder().withAmount(windupResource.getSpec().getExecutor_cpu_request()).build()))
                            .addToRequests(Map.of("memory", new QuantityBuilder().withAmount(windupResource.getSpec().getExecutor_mem_request()).build()))
                            .addToLimits(Map.of("cpu", new QuantityBuilder().withAmount(windupResource.getSpec().getExecutor_cpu_request()).build()))
                            .addToLimits(Map.of("memory", new QuantityBuilder().withAmount(windupResource.getSpec().getExecutor_mem_request()).build()))
                          .endResources()
                          .withVolumeMounts(List.of(
                                new VolumeMountBuilder().withName(windupResource.getSpec().getApplication_name() + "-postgresql-pvol").withMountPath("/var/lib/pgsql/data").build()
                                ))
                          .addNewEnv().withName("POSTGRESQL_USER").withValue(windupResource.getSpec().getDb_username()).endEnv()
                        .addNewEnv().withName("POSTGRESQL_PASSWORD").withValue(windupResource.getSpec().getDb_password()).endEnv()
                        .addNewEnv().withName("POSTGRESQL_DATABASE").withValue(windupResource.getSpec().getDb_database()).endEnv()
                        .addNewEnv().withName("POSTGRESQL_MAX_CONNECTIONS").withValue(windupResource.getSpec().getPostgresql_max_connections()).endEnv()
                        .addNewEnv().withName("POSTGRESQL_MAX_PREPARED_TRANSACTIONS").withValue(windupResource.getSpec().getPostgresql_max_connections()).endEnv()
                        .addNewEnv().withName("POSTGRESQL_SHARED_BUFFERS").withValue(windupResource.getSpec().getPostgresql_shared_buffers()).endEnv()
                        .endContainer()
                        .addNewVolume().withName(windupResource.getSpec().getApplication_name() + "-postgres-pvol").withNewPersistentVolumeClaim().withClaimName(windupResource.getSpec().getApplication_name() + "-postgresql-claim").endPersistentVolumeClaim().endVolume()
                     .endSpec()
                 .endTemplate()
              .endSpec()
              .build();

        Deployment deploymentPostgre = new DeploymentBuilder()
            .withNewMetadata()
               .withName(windupResource.getSpec().getApplication_name() + "-postgresql")
               .addToLabels("application",windupResource.getSpec().getApplication_name() + "-postgresql")
            .endMetadata()
            .withNewSpec()
               .withReplicas(1)
               .withNewSelector()
                   .addToMatchLabels("deploymentConfig",windupResource.getSpec().getApplication_name() + "-postgresql")
               .endSelector()
               .withNewStrategy()
                  .withType("Recreate")
                .endStrategy()
               .withNewTemplate()
                   .withNewMetadata()
                      .addToLabels("deploymentConfig",windupResource.getSpec().getApplication_name() + "-postgresql")
                      .addToLabels("application",windupResource.getSpec().getApplication_name() + "-postgresql")
                      .withName(windupResource.getSpec().getApplication_name())
                   .endMetadata()
                   .withNewSpec()
                      .withTerminationGracePeriodSeconds(60L)
                      .addNewContainer()
                          .withName(windupResource.getSpec().getApplication_name() + "-postgresql")
                          .withImage("postgresql")
                          .withNewImagePullPolicy("Always")
                          .withNewResources()
                             .addToRequests(Map.of("cpu", new QuantityBuilder().withAmount(windupResource.getSpec().getPostgresql_cpu_request()).build()))
                             .addToRequests(Map.of("memory", new QuantityBuilder().withAmount(windupResource.getSpec().getPostgresql_mem_request()).build()))
                             .addToLimits(Map.of("cpu", new QuantityBuilder().withAmount(windupResource.getSpec().getPostgresql_cpu_limit()).build()))
                             .addToLimits(Map.of("memory", new QuantityBuilder().withAmount(windupResource.getSpec().getPostgresql_mem_limit()).build()))
                          .endResources()
                          .withVolumeMounts(List.of(
                                new VolumeMountBuilder().withName(windupResource.getSpec().getApplication_name() + "-mta-web-pvol").withMountPath("/opt/eap/standalone/data/windup").withReadOnly(false).build(),
                                new VolumeMountBuilder().withName(windupResource.getSpec().getApplication_name() + "-mta-web-executor-volume").withMountPath("/opt/eap/standalone/data").withReadOnly(false).build()
                                ))
                          .withNewLifecycle()
                             .withNewPreStop()
                               .withNewExec()
                                 .withCommand("/opt/mta-cli/bin/stop.sh")
                               .endExec()
                             .endPreStop()
                          .endLifecycle()
                          .withNewLivenessProbe()
                            .withNewExec()
                                .withCommand("/bin/bash","-c","/opt/mta-cli/bin/livenessProbe.sh")
                            .endExec()
                            .withInitialDelaySeconds(120)
                            .withFailureThreshold(3)
                            .withSuccessThreshold(1)
                            .withTimeoutSeconds(10)
                          .endLivenessProbe()
                          .withNewReadinessProbe()
                            .withNewExec()
                                .withCommand("/bin/bash","-c","/opt/mta-cli/bin/livenessProbe.sh")
                            .endExec()
                            .withInitialDelaySeconds(120)
                            .withFailureThreshold(3)
                            .withSuccessThreshold(1)
                            .withTimeoutSeconds(10)
                          .endReadinessProbe()
                          .addNewEnv().withName("IS_MASTER").withValue("false").endEnv()
                        .addNewEnv().withName("MESSAGING_SERIALIZER").withValue(windupResource.getSpec().getMessaging_serializer()).endEnv()
                        .addNewEnv().withName("GRAPH_BASE_OUTPUT_PATH").withValue("/opt/eap/standalone/data/windup-graphs").endEnv()
                        .addNewEnv().withName("MESSAGING_HOST_VAR").withValue(windupResource.getSpec().getApplication_name() + "_SERVICE_HOST").endEnv()
                        .addNewEnv().withName("MESSAGING_PASSWORD").withValue("gthudfal").endEnv()
                        .addNewEnv().withName("MESSAGING_USER").withValue("jms-user").endEnv()
                        .endContainer()
                        .addNewVolume().withName(windupResource.getSpec().getApplication_name() + "-mta-web-pvol").withNewPersistentVolumeClaim().withClaimName(windupResource.getSpec().getApplication_name() + "-mta-web-claim").endPersistentVolumeClaim().endVolume()
                        .addNewVolume().withName(windupResource.getSpec().getApplication_name() + "-mta-web-executor-volume").withNewEmptyDir().endEmptyDir().endVolume()
                     .endSpec()
                 .endTemplate()
              .endSpec()
              .build();
        return List.of(deploymentMTAweb, deploymentExecutor, deploymentPostgre);
    }
}