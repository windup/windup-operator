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
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class WindupDeployment {
    private static final Logger LOG = Logger.getLogger(WindupDeployment.class);

    @Inject
    private KubernetesClient k8sClient;

    public void deployWindup() {
        k8sClient.apps().deployments().inNamespace("default").createOrReplace(createDeployment().toArray(new Deployment[3]));
        k8sClient.services().inNamespace("default").createOrReplace(createServices().toArray(new Service[3]));
        k8sClient.network().ingress().inNamespace("default").createOrReplace(createIngresses().toArray(new Ingress[2]));
        k8sClient.persistentVolumeClaims().createOrReplace(createVolumes().toArray(new PersistentVolumeClaim[2]));
    }

    private List<Service> createServices() {
        Service mtaWebConsoleSvc = k8sClient.services().createNew()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName("${APPLICATION_NAME}")
          .withLabels(Map.of("application", "${APPLICATION_NAME}", "app", "${APPLICATION_NAME}"))
          .withAnnotations(Map.of("description", "The web server's http port", "service.alpha.openshift.io/dependencies", "[{\"name\": \"${APPLICATION_NAME}-postgresql\", \"kind\": \"Service\"}]"))
        .endMetadata()
        .withNewSpec()
          .withPorts(Collections.singletonList(new ServicePort("a", "name", 0, 8080, "", new IntOrString(8080) )))
          .withSelector(Collections.singletonMap("deploymentConfig", "${APPLICATION_NAME}"))
        .endSpec().done();

        Service postgreSvc = k8sClient.services().createNew()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName("${APPLICATION_NAME}-postgresql")
          .withLabels(Map.of("application", "${APPLICATION_NAME}", "app", "${APPLICATION_NAME}"))
          .withAnnotations(Map.of("description", "The web server's http port", "service.alpha.openshift.io/dependencies", "[{\"name\": \"${APPLICATION_NAME}-postgresql\", \"kind\": \"Service\"}]"))
        .endMetadata()
        .withNewSpec()
          .withPorts(new ServicePort("a", "name", 0, 5432, "", new IntOrString(5432) ))
          .withSelector(Collections.singletonMap("deploymentConfig", "${APPLICATION_NAME}-postgresql"))
        .endSpec().done();

        Service amqSvc = k8sClient.services().createNew()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName("${APPLICATION_NAME}-amq")
          .withLabels(Map.of("application", "${APPLICATION_NAME}-amq"))
          .withAnnotations(Map.of("description", "MTA Master AMQ port."))
        .and()
        .withNewSpec()
          .withPorts(new ServicePort("a", "name", 0, 61616, "", new IntOrString(61616) ))
          .withSelector(Collections.singletonMap("deploymentConfig", "${APPLICATION_NAME}"))
        .and().done();

        return List.of(mtaWebConsoleSvc, postgreSvc, postgreSvc);
    }

    private List<Ingress> createIngresses() {
        Ingress ingressWebConsole = new IngressBuilder()
          .withNewMetadata()
             .withName("${APPLICATION_NAME}")
             .addToLabels("application", "${APPLICATION_NAME}")
             .addToAnnotations("description", "Route for application's http service.")
             .addToAnnotations("console.alpha.openshift.io/overview-app-route","true")
          .endMetadata()
          .withNewSpec()
            .addToTls(new IngressTLSBuilder().withHosts("${HOSTNAME_HTTP}").build())
            .addNewRule()
                .withHost("${HOSTNAME_HTTP}")
              .withNewHttp()
                .addNewPath()
                  .withPath("/").withNewBackend().withServiceName("${APPLICATION_NAME}").withServicePort(new IntOrString(80)).endBackend()
                .endPath()
              .endHttp()
            .endRule()
          .endSpec()
        .build();

        return List.of(ingressWebConsole);
    }

    private List<PersistentVolumeClaim> createVolumes() {
        PersistentVolumeClaim postgrPersistentVolumeClaim = new PersistentVolumeClaimBuilder()
        .withNewMetadata().withName("${APPLICATION_NAME}-postgresql-claim").addToLabels("application", "${APPLICATION_NAME}").endMetadata()
        .withNewSpec()
            .withAccessModes("ReadWriteOne")
            .withNewResources().addToRequests("storage", new Quantity("${VOLUME_CAPACITY}")).endResources()
        .endSpec()
        .build();        
        
        PersistentVolumeClaim mtaPersistentVolumeClaim = new PersistentVolumeClaimBuilder()
        .withNewMetadata().withName("${APPLICATION_NAME}-mta-web-claim").addToLabels("application", "${APPLICATION_NAME}").endMetadata()
        .withNewSpec()
            .withAccessModes("ReadWriteMany")
            .withNewResources().addToRequests("storage", new Quantity("${MTA_VOLUME_CAPACITY}")).endResources()
        .endSpec()
        .build();
        return List.of(postgrPersistentVolumeClaim, mtaPersistentVolumeClaim);
    }

    private List<Deployment> createDeployment() {
        Deployment deploymentMTAweb = new DeploymentBuilder()
            .withNewMetadata()
               .withName("${APPLICATION_NAME}")
               .addToLabels("application", "${APPLICATION_NAME}")
            .endMetadata()
            .withNewSpec()
               .withReplicas(1)
               .withNewSelector()
                   .addToMatchLabels("deploymentConfig", "${APPLICATION_NAME}")
               .endSelector()
               .withNewStrategy()
                  .withType("Recreate")
                .endStrategy()
               .withNewTemplate()
                   .withNewMetadata()
                      .addToLabels("deploymentConfig", "${APPLICATION_NAME}")
                      .addToLabels("application", "${APPLICATION_NAME}")
                      .withName("${APPLICATION_NAME}")
                   .endMetadata()
                   .withNewSpec()
                      .withTerminationGracePeriodSeconds(75L)
                      .addNewContainer()
                          .withName("${APPLICATION_NAME}")
                          .withImage("docker.io/${DOCKER_IMAGES_USER}/windup-web-openshift:${DOCKER_IMAGES_TAG}")
                          .withNewImagePullPolicy("Always")
                          .withNewResources()
                             .addToRequests(Map.of("cpu", new QuantityBuilder().withAmount("1").build()))
                             .addToRequests(Map.of("memory", new QuantityBuilder().withAmount("2GB").build()))
                             .addToLimits(Map.of("cpu", new QuantityBuilder().withAmount("1").build()))
                             .addToLimits(Map.of("memory", new QuantityBuilder().withAmount("2GB").build()))
                          .endResources()
                          .withVolumeMounts(List.of(
                                new VolumeMountBuilder().withName("${APPLICATION_NAME}-mta-web-pvol").withMountPath("/opt/eap/standalone/data/windup").withReadOnly(false).build(),
                                new VolumeMountBuilder().withName("${APPLICATION_NAME}-mta-web-pvol-data").withMountPath("/opt/eap/standalone/data").withReadOnly(false).build()
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
                            .withInitialDelaySeconds(120)
                            .withFailureThreshold(3)
                            .withSuccessThreshold(1)
                            .withTimeoutSeconds(10)
                          .endLivenessProbe()
                          .withNewReadinessProbe()
                            .withNewExec()
                                .withCommand("/bin/bash","-c","/opt/eap/bin/readinessProbe.sh")
                            .endExec()
                            .withInitialDelaySeconds(120)
                            .withFailureThreshold(3)
                            .withSuccessThreshold(1)
                            .withTimeoutSeconds(10)
                          .endReadinessProbe()
                          .addNewPort().withName("jolokia").withContainerPort(8778).withProtocol("TCP").endPort()
                          .addNewPort().withName("http").withContainerPort(8080).withProtocol("TCP").endPort()
                          .addNewPort().withName("ping").withContainerPort(8888).withProtocol("TCP").endPort()
                          .addNewEnv().withName("IS_MASTER").withValue("true").endEnv()
                        .addNewEnv().withName("MESSAGING_SERIALIZER").withValue("${MESSAGING_SERIALIZER}").endEnv()
                        .addNewEnv().withName("GRAPH_BASE_OUTPUT_PATH").withValue("/opt/eap/standalone/data/windup-graphs").endEnv()
                        .addNewEnv().withName("DB_SERVICE_PREFIX_MAPPING").withValue("${APPLICATION_NAME}-postgresql=DB").endEnv()
                        .addNewEnv().withName("DB_JNDI").withValue("${DB_JNDI}").endEnv()
                        .addNewEnv().withName("DB_USERNAME").withValue("${DB_USERNAME}").endEnv()
                        .addNewEnv().withName("DB_PASSWORD").withValue("${DB_PASSWORD}").endEnv()
                        .addNewEnv().withName("DB_DATABASE").withValue("${DB_DATABASE}").endEnv()
                        .addNewEnv().withName("TX_DATABASE_PREFIX_MAPPING").withValue("${APPLICATION_NAME}-postgresql=DB").endEnv()
                        .addNewEnv().withName("DB_MIN_POOL_SIZE").withValue("${DB_MIN_POOL_SIZE}").endEnv()
                        .addNewEnv().withName("DB_MAX_POOL_SIZE").withValue("${DB_MAX_POOL_SIZE}").endEnv()
                        .addNewEnv().withName("DB_TX_ISOLATION").withValue("${DB_TX_ISOLATION}").endEnv()
                        .addNewEnv().withName("OPENSHIFT_KUBE_PING_LABELS").withValue("application=${APPLICATION_NAME}").endEnv()
                        .addNewEnv().withName("OPENSHIFT_KUBE_PING_NAMESPACE").withNewValueFrom().withNewFieldRef("apiVersion", "metadata.namespace").endValueFrom().endEnv()
                        .addNewEnv().withName("HTTPS_KEYSTORE_DIR").withValue("/etc/eap-secret-volume").endEnv()
                        .addNewEnv().withName("MQ_CLUSTER_PASSWORD").withValue("${MQ_CLUSTER_PASSWORD}").endEnv()
                        .addNewEnv().withName("MQ_QUEUES").withValue("${MQ_QUEUES}").endEnv()
                        .addNewEnv().withName("MQ_TOPICS").withValue("${MQ_TOPICS}").endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_SECRET").withValue("${JGROUPS_ENCRYPT_SECRET}").endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE_DIR").withValue("/etc/jgroups-encrypt-secret-volume").endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE").withValue("${JGROUPS_ENCRYPT_KEYSTORE}").endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_NAME").withValue("${JGROUPS_ENCRYPT_NAME}").endEnv()
                        .addNewEnv().withName("JGROUPS_ENCRYPT_PASSWORD").withValue("${JGROUPS_ENCRYPT_PASSWORD}").endEnv()
                        .addNewEnv().withName("JGROUPS_CLUSTER_PASSWORD").withValue("${JGROUPS_CLUSTER_PASSWORD}").endEnv()
                        .addNewEnv().withName("AUTO_DEPLOY_EXPLODED").withValue("${AUTO_DEPLOY_EXPLODED}").endEnv()
                        .addNewEnv().withName("DEFAULT_JOB_REPOSITORY").withValue("${APPLICATION_NAME}-postgresql").endEnv()
                        .addNewEnv().withName("TIMER_SERVICE_DATA_STORE").withValue("${APPLICATION_NAME}-postgresql").endEnv()
                        .addNewEnv().withName("SSO_URL").withValue("${SSO_URL}").endEnv()
                        .addNewEnv().withName("SSO_SERVICE_URL").withValue("${SSO_SERVICE_URL}").endEnv()
                        .addNewEnv().withName("SSO_REALM").withValue("${SSO_REALM}").endEnv()
                        .addNewEnv().withName("SSO_USERNAME").withValue("${SSO_USERNAME}").endEnv()
                        .addNewEnv().withName("SSO_PASSWORD").withValue("${SSO_PASSWORD}").endEnv()
                        .addNewEnv().withName("SSO_PUBLIC_KEY").withValue("${SSO_PUBLIC_KEY}").endEnv()
                        .addNewEnv().withName("SSO_BEARER_ONLY").withValue("${SSO_BEARER_ONLY}").endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE_SECRET").withValue("${SSO_SAML_KEYSTORE_SECRET}").endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE").withValue("${SSO_SAML_KEYSTORE}").endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE_DIR").withValue("/etc/sso-saml-secret-volume").endEnv()
                        .addNewEnv().withName("SSO_SAML_CERTIFICATE_NAME").withValue("${SSO_SAML_CERTIFICATE_NAME}").endEnv()
                        .addNewEnv().withName("SSO_SAML_KEYSTORE_PASSWORD").withValue("${SSO_SAML_KEYSTORE_PASSWORD}").endEnv()
                        .addNewEnv().withName("SSO_SECRET").withValue("${SSO_SECRET}").endEnv()
                        .addNewEnv().withName("SSO_ENABLE_CORS").withValue("${SSO_ENABLE_CORS}").endEnv()
                        .addNewEnv().withName("SSO_SAML_LOGOUT_PAGE").withValue("${SSO_SAML_LOGOUT_PAGE}").endEnv()
                        .addNewEnv().withName("SSO_DISABLE_SSL_CERTIFICATE_VALIDATION").withValue("${SSO_DISABLE_SSL_CERTIFICATE_VALIDATION}").endEnv()
                        .addNewEnv().withName("SSO_TRUSTSTORE").withValue("${SSO_TRUSTSTORE}").endEnv()
                        .addNewEnv().withName("SSO_TRUSTSTORE_DIR").withValue("/etc/sso-secret-volume").endEnv()
                        .addNewEnv().withName("SSO_TRUSTSTORE_PASSWORD").withValue("${SSO_TRUSTSTORE_PASSWORD}").endEnv()
                        .addNewEnv().withName("GC_MAX_METASPACE_SIZE").withValue("512").endEnv()
                        .addNewEnv().withName("MAX_POST_SIZE").withValue("${MAX_POST_SIZE}").endEnv()
                      .endContainer()
                      .addNewVolume().withName("${APPLICATION_NAME}-mta-web-pvol").withNewPersistentVolumeClaim().withClaimName("${APPLICATION_NAME}-mta-web-claim").endPersistentVolumeClaim().endVolume()
                      .addNewVolume().withName("${APPLICATION_NAME}-mta-web-pvol-data").withNewEmptyDir().endEmptyDir().endVolume()
                   .endSpec()
               .endTemplate()
            .endSpec()
            .build();

        Deployment deploymentExecutor = new DeploymentBuilder()
            .withNewMetadata()
               .withName("${APPLICATION_NAME}-executor")
               .addToLabels("application", "${APPLICATION_NAME}-executor")
            .endMetadata()
            .withNewSpec()
               .withReplicas(1)
               .withNewSelector()
                   .addToMatchLabels("deploymentConfig", "${APPLICATION_NAME}-executor")
               .endSelector()
               .withNewStrategy()
                  .withType("Recreate")
                .endStrategy()
               .withNewTemplate()
                   .withNewMetadata()
                      .addToLabels("deploymentConfig", "${APPLICATION_NAME}-executor")
                      .addToLabels("application", "${APPLICATION_NAME}-executor")
                      .withName("${APPLICATION_NAME}-executor")
                   .endMetadata()
                   .withNewSpec()
                      .withTerminationGracePeriodSeconds(75L)
                      .addNewContainer()
                          .withName("${APPLICATION_NAME}-executor")
                          .withImage("docker.io/${DOCKER_IMAGES_USER}/windup-web-openshift-messaging-executor:${DOCKER_IMAGES_TAG}")
                          .addNewPort().withName("postgre").withContainerPort(8778).withProtocol("TCP").endPort()
                          .withNewImagePullPolicy("Always")
                          .withVolumeMounts(List.of(
                                new VolumeMountBuilder().withName("${APPLICATION_NAME}-postgresql-pvol").withMountPath("/var/lib/pgsql/data").build()
                                ))
                          .addNewEnv().withName("POSTGRESQL_USER").withValue("${DB_USERNAME}").endEnv()
                        .addNewEnv().withName("POSTGRESQL_PASSWORD").withValue("${DB_PASSWORD}").endEnv()
                        .addNewEnv().withName("POSTGRESQL_DATABASE").withValue("${DB_DATABASE}").endEnv()
                        .addNewEnv().withName("POSTGRESQL_MAX_CONNECTIONS").withValue("${POSTGRESQL_MAX_CONNECTIONS}").endEnv()
                        .addNewEnv().withName("POSTGRESQL_MAX_PREPARED_TRANSACTIONS").withValue("${POSTGRESQL_MAX_CONNECTIONS}").endEnv()
                        .addNewEnv().withName("POSTGRESQL_SHARED_BUFFERS").withValue("${POSTGRESQL_SHARED_BUFFERS}").endEnv()
                        .endContainer()
                        .addNewVolume().withName("${APPLICATION_NAME}-postgres-pvol").withNewPersistentVolumeClaim().withClaimName("${APPLICATION_NAME}-postgresql-claim").endPersistentVolumeClaim().endVolume()
                     .endSpec()
                 .endTemplate()
              .endSpec()
              .build();
       
        Deployment deploymentPostgre = new DeploymentBuilder()
            .withNewMetadata()
               .withName("${APPLICATION_NAME}-postgresql")
               .addToLabels("application", "${APPLICATION_NAME}-postgresql")
            .endMetadata()
            .withNewSpec()
               .withReplicas(1)
               .withNewSelector()
                   .addToMatchLabels("deploymentConfig", "${APPLICATION_NAME}-postgresql")
               .endSelector()
               .withNewStrategy()
                  .withType("Recreate")
                .endStrategy()
               .withNewTemplate()
                   .withNewMetadata()
                      .addToLabels("deploymentConfig", "${APPLICATION_NAME}-postgresql")
                      .addToLabels("application", "${APPLICATION_NAME}-postgresql")
                      .withName("${APPLICATION_NAME}")
                   .endMetadata()
                   .withNewSpec()
                      .withTerminationGracePeriodSeconds(60L)
                      .addNewContainer()
                          .withName("${APPLICATION_NAME}-postgresql")
                          .withImage("postgresql")
                          .withNewImagePullPolicy("Always")
                          .withNewResources()
                             .addToRequests(Map.of("cpu", new QuantityBuilder().withAmount("1").build()))
                             .addToRequests(Map.of("memory", new QuantityBuilder().withAmount("2GB").build()))
                             .addToLimits(Map.of("cpu", new QuantityBuilder().withAmount("1").build()))
                             .addToLimits(Map.of("memory", new QuantityBuilder().withAmount("2GB").build()))
                          .endResources()
                          .withVolumeMounts(List.of(
                                new VolumeMountBuilder().withName("${APPLICATION_NAME}-mta-web-pvol").withMountPath("/opt/eap/standalone/data/windup").withReadOnly(false).build(),
                                new VolumeMountBuilder().withName("${APPLICATION_NAME}-mta-web-executor-volume").withMountPath("/opt/eap/standalone/data").withReadOnly(false).build()
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
                        .addNewEnv().withName("MESSAGING_SERIALIZER").withValue("${MESSAGING_SERIALIZER}").endEnv()
                        .addNewEnv().withName("GRAPH_BASE_OUTPUT_PATH").withValue("/opt/eap/standalone/data/windup-graphs").endEnv()
                        .addNewEnv().withName("MESSAGING_HOST_VAR").withValue("${APPLICATION_NAME}_SERVICE_HOST").endEnv()
                        .addNewEnv().withName("MESSAGING_PASSWORD").withValue("gthudfal").endEnv()
                        .addNewEnv().withName("MESSAGING_USER").withValue("jms-user").endEnv()
                        .endContainer()
                        .addNewVolume().withName("${APPLICATION_NAME}-mta-web-pvol").withNewPersistentVolumeClaim().withClaimName("${APPLICATION_NAME}-mta-web-claim").endPersistentVolumeClaim().endVolume()
                        .addNewVolume().withName("${APPLICATION_NAME}-mta-web-executor-volume").withNewEmptyDir().endEmptyDir().endVolume()
                     .endSpec()
                 .endTemplate()
              .endSpec()
              .build();

        


        return List.of(deploymentMTAweb, deploymentExecutor, deploymentPostgre);
    }

}