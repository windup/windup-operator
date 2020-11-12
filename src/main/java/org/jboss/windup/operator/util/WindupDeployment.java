package org.jboss.windup.operator.util;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1beta1.IngressTLSBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.java.Log;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceDoneable;
import org.jboss.windup.operator.model.WindupResourceList;

import javax.inject.Named;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log
public class WindupDeployment {

  @ConfigProperty(name = "operator.serviceaccount", defaultValue = "windup-operator")
  String serviceAccount;

  @Named("namespace")
  String namespace;

  MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient;

  KubernetesClient k8sClient;

  private WindupResource windupResource;

  private String deployment_executor;
  private String deployment_postgre;
  private String volume_postgresql_claim;
  private String volume_mtaweb_claim;
  private String volume_postgresql;
  private String volume_executor;
  private String volume_mtaweb;
  private String volume_mtaweb_data;

  public WindupDeployment(WindupResource windupResource, MixedOperation<WindupResource, WindupResourceList, WindupResourceDoneable, Resource<WindupResource, WindupResourceDoneable>> crClient, KubernetesClient k8sClient) {
    this.windupResource = windupResource;
    this.crClient = crClient;
    this.k8sClient = k8sClient;

    // Init names of objects
    volume_postgresql_claim = windupResource.getSpec().getApplication_name() + "-postgresql-claim";
    deployment_postgre = windupResource.getSpec().getApplication_name() + "-postgresql";
    deployment_executor = windupResource.getSpec().getApplication_name() + "-executor";
    volume_mtaweb_claim = windupResource.getSpec().getApplication_name() + "-mta-web-claim";
    volume_postgresql = windupResource.getSpec().getApplication_name() + "-postgresql-pvol";
    volume_executor = windupResource.getSpec().getApplication_name() + "-mta-web-executor-volume";
    volume_mtaweb = windupResource.getSpec().getApplication_name() + "-mta-web-pvol";
    volume_mtaweb_data = windupResource.getSpec().getApplication_name() + "-mta-web-pvol-data";
  }

  public void deploy() {
    // We are adding one by one instead of createOrReplace(volumes.toArray(new Volume[2])) 
    // because in that case we receive an error : Too Many Items to Create
    initCRStatusOnDeployment();

    List<PersistentVolumeClaim> volumes = createVolumes();
    k8sClient.persistentVolumeClaims().inNamespace(namespace).createOrReplace(volumes.get(0));    
    k8sClient.persistentVolumeClaims().inNamespace(namespace).createOrReplace(volumes.get(1));

    List<Deployment> deployments = createDeployment();
    k8sClient.apps().deployments().inNamespace(namespace).createOrReplace(deployments.get(0));
    k8sClient.apps().deployments().inNamespace(namespace).createOrReplace(deployments.get(1));
    k8sClient.apps().deployments().inNamespace(namespace).createOrReplace(deployments.get(2));

    List<Service> services = createServices();
    k8sClient.services().inNamespace(namespace).createOrReplace(services.get(0));
    k8sClient.services().inNamespace(namespace).createOrReplace(services.get(1));
    k8sClient.services().inNamespace(namespace).createOrReplace(services.get(2));

    List<Ingress> ingresses = createIngresses();
    k8sClient.network().ingress().inNamespace(namespace).createOrReplace(ingresses.get(0));
    k8sClient.network().ingress().inNamespace(namespace).createOrReplace(ingresses.get(1));
  }

  private void initCRStatusOnDeployment() {
    windupResource.initStatus();
    windupResource.setStatusDeploy(true);
    windupResource.setReady(false);

    log.info("updating status : " + windupResource.getMetadata().getName() + " crc " + crClient);
    crClient.inNamespace(namespace).updateStatus(windupResource);
  }

  // @format:off
  private List<Service> createServices() {
    Service mtaWebConsoleSvc = new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName(windupResource.getSpec().getApplication_name())
          .withLabels(getLabels())
          .addToAnnotations("description", "The web server's http port")
          .addToAnnotations("service.alpha.openshift.io/dependencies","[{\"name\": \"" + windupResource.getSpec().getApplication_name()+ "-postgresql\", \"kind\": \"Service\"}]")
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .addNewPort()
            .withName("web-port")
            .withPort(8080)
            .withTargetPort(new IntOrString(8080))
          .endPort()
          .withSelector(Collections.singletonMap("deploymentConfig", windupResource.getSpec().getApplication_name()))
        .endSpec().build();
    log.info("Created Service for windup");

    Service postgreSvc = new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName(deployment_postgre)
          .withLabels(getLabels())
          .addToAnnotations("description", "The database server's port.")
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .addNewPort()
            .withName("postgre-port")
            .withPort(5432)
            .withTargetPort(new IntOrString(5432))
          .endPort()
          .withSelector(Collections.singletonMap("deploymentConfig", deployment_postgre))
        .endSpec().build();
    log.info("Created Service for postgresql");

    Service amqSvc = new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName(windupResource.getSpec().getApplication_name() + "-amq")
          .withLabels(getLabels())
          .addToAnnotations("description", "MTA Master AMQ port.")
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .addNewPort()
            .withName("amq-port")
            .withPort(61616)
            .withTargetPort(new IntOrString(61616))
          .endPort()
          .withSelector(Collections.singletonMap("deploymentConfig", windupResource.getSpec().getApplication_name() + "-amq"))
        .endSpec().build();
    log.info("Created Service for AMQ");

    return List.of(mtaWebConsoleSvc, postgreSvc, postgreSvc, amqSvc);
  }

  private OwnerReference getOwnerReference() {
    return new OwnerReferenceBuilder()
          .withController(true)
          .withKind(windupResource.getKind())
          .withApiVersion(windupResource.getApiVersion())
          .withName(windupResource.getMetadata().getName())
          .withNewUid(windupResource.getMetadata().getUid())
        .build();
}

private Map<String, String> getLabels() {
    return Map.of(
        "application", windupResource.getSpec().getApplication_name(),
        "app", windupResource.getSpec().getApplication_name(),
        "created-by", "mta-operator");
  }

  // Checking the cluster domain on Openshift
  private String getClusterDomainOnOpenshift() {
    String clusterDomain = "";
    try {
      ConfigMap configMap = k8sClient.configMaps().inNamespace("openshift-config-managed").withName("console-public").get();

      if (configMap != null) {
        clusterDomain = configMap.getData().getOrDefault("consoleURL", "");
        clusterDomain = clusterDomain.replace("https://console-openshift-console", "");
      }
    } catch (KubernetesClientException exception) {
      log.info("You are probably not on Openshift");
    }

    return clusterDomain;
  }

  private List<Ingress> createIngresses() {
    String hostnameHttp = windupResource.getSpec().getHostname_http();

    // if the user doesn't provide hostname we'll try to discover it on Openshift
    // if we are in K8s then cluster domain will be blank
    if (StringUtils.isBlank(hostnameHttp)) {
      hostnameHttp = getClusterDomainOnOpenshift();
      log.info("Cluster Domain : " + hostnameHttp);
    }

    Ingress ingressWebConsoleHttps = createWebConsoleHttpsIngress(hostnameHttp);

    Ingress ingressWebConsole = createWebConsoleHttpIngress(hostnameHttp);

    return List.of(ingressWebConsoleHttps, ingressWebConsole);
  }

  private Ingress createWebConsoleHttpIngress(String hostnameHttp) {
    return new IngressBuilder()
                .withNewMetadata()
                  .withName(windupResource.getSpec().getApplication_name())
                  .withLabels(getLabels())
                  .addToAnnotations("description", "Route for application's http service.")
                  .addToAnnotations("console.alpha.openshift.io/overview-app-route", "true")
                  .withOwnerReferences(getOwnerReference())
                .endMetadata()
                .withNewSpec()
                  .addNewRule()
                    .withHost(windupResource.getSpec().getApplication_name() + hostnameHttp)
                    .withNewHttp()
                      .addNewPath()
                        .withPath("/")
                          .withNewBackend()
                            .withServiceName(windupResource.getSpec().getApplication_name())
                            .withServicePort(new IntOrString(8080))
                          .endBackend()
                        .endPath()
                    .endHttp()
                  .endRule()
                .endSpec().build();
  }

  private Ingress createWebConsoleHttpsIngress(String hostnameHttp) {
    String ingressName = "secure-" + windupResource.getSpec().getApplication_name();
    String hostHTTPS = ingressName + hostnameHttp;

    // We will use the same HTTP ingress but we'll add what's needed for HTTPS
    Ingress ingress = createWebConsoleHttpIngress(hostnameHttp);
    ingress.getMetadata().setName(ingressName);
    ingress.getMetadata().getAnnotations().remove("console.alpha.openshift.io/overview-app-route");
    ingress.getSpec().setTls(Collections.singletonList(new IngressTLSBuilder().withHosts(hostHTTPS).build()));
    ingress.getSpec().getRules().get(0).setHost(hostHTTPS);

    return ingress;
  }

  private List<PersistentVolumeClaim> createVolumes() {
    PersistentVolumeClaim postgrPersistentVolumeClaim = new PersistentVolumeClaimBuilder()
        .withNewMetadata()
          .withName(volume_postgresql_claim)
          .withLabels(getLabels())
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .withAccessModes("ReadWriteOnce")
          .withNewResources()
            .addToRequests("storage", new Quantity(windupResource.getSpec().getVolumeCapacity()))
          .endResources()
        .endSpec().build();
    log.info("Created PVC for postgre");

    PersistentVolumeClaim mtaPersistentVolumeClaim = new PersistentVolumeClaimBuilder()
        .withNewMetadata()
          .withName(volume_mtaweb_claim)
          .withLabels(getLabels())
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .withAccessModes("ReadWriteOnce")
          .withNewResources()
            .addToRequests("storage", new Quantity(windupResource.getSpec().getMta_Volume_Capacity()))
          .endResources()
        .endSpec().build();
    log.info("Created PVC for mta");

    return List.of(postgrPersistentVolumeClaim, mtaPersistentVolumeClaim);
  }

  private List<Deployment> createDeployment() {
    Deployment deploymentMTAweb = new DeploymentBuilder()
        .withNewMetadata()
          .withName(windupResource.getSpec().getApplication_name())
          .withLabels(getLabels())
          .withOwnerReferences(getOwnerReference())
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
              .withLabels(getLabels())
              .addToLabels("deploymentConfig", windupResource.getSpec().getApplication_name())
              .withName(windupResource.getSpec().getApplication_name())
            .endMetadata()
            .withNewSpec()
              .withServiceAccount(serviceAccount).withTerminationGracePeriodSeconds(75L)
              .addNewContainer()
                .withName(windupResource.getSpec().getApplication_name())
                .withImage(getContainerImageName( windupResource.getSpec().getDocker_image_web()))
                .withNewImagePullPolicy("Always")
                .withNewResources()
                  .addToRequests(Map.of("cpu", new Quantity(windupResource.getSpec().getWeb_cpu_request())))
                  .addToRequests(Map.of("memory", new Quantity(windupResource.getSpec().getWeb_mem_request())))
                  .addToLimits(Map.of("cpu", new Quantity(windupResource.getSpec().getWeb_cpu_limit())))
                  .addToLimits(Map.of("memory", new Quantity(windupResource.getSpec().getWeb_mem_limit())))
                .endResources()
                .addToVolumeMounts(new VolumeMountBuilder()
                                    .withName(volume_mtaweb)
                                    .withMountPath("/opt/eap/standalone/data/windup")
                                    .withReadOnly(false)
                                    .build())
                .addToVolumeMounts(new VolumeMountBuilder()
                                    .withName(volume_mtaweb_data)
                                    .withMountPath("/opt/eap/standalone/data")
                                    .withReadOnly(false)
                                    .build())
                .withNewLifecycle()
                  .withNewPreStop()
                    .withNewExec()
                      .withCommand("/opt/eap/bin/jboss-cli.sh", "-c", ":shutdown(timeout=60)")
                    .endExec()
                  .endPreStop()
                .endLifecycle()
                .withNewLivenessProbe()
                  .withNewExec()
                    .withCommand(Arrays.stream(windupResource.getSpec().getWeb_liveness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "/opt/eap/bin/livenessProbe.sh")
                  .endExec()
                  .withInitialDelaySeconds(Integer.parseInt(windupResource.getSpec().getWebLivenessInitialDelaySeconds()))
                  .withFailureThreshold(Integer.parseInt(windupResource.getSpec().getWebLivenessFailureThreshold()))
                  .withSuccessThreshold(1)
                  .withTimeoutSeconds(Integer.parseInt(windupResource.getSpec().getWebLivenessTimeoutSeconds()))
                .endLivenessProbe()
                .withNewReadinessProbe()
                  .withNewExec()
                    .withCommand(Arrays.stream(windupResource.getSpec().getWeb_readiness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "/opt/eap/bin/readinessProbe.sh")
                  .endExec()
                  .withInitialDelaySeconds(Integer.parseInt(windupResource.getSpec().getWebReadinessInitialDelaySeconds()))
                  .withFailureThreshold(Integer.parseInt(windupResource.getSpec().getWebReadinessFailureThreshold()))
                  .withSuccessThreshold(1)
                  .withTimeoutSeconds(Integer.parseInt(windupResource.getSpec().getWebReadinessTimeoutSeconds()))
                .endReadinessProbe()
                .addNewPort().withName("jolokia").withContainerPort(8778).withProtocol("TCP").endPort()
                .addNewPort().withName("http").withContainerPort(8080).withProtocol("TCP").endPort()
                .addNewPort().withName("ping").withContainerPort(8888).withProtocol("TCP").endPort()
                .addNewEnv().withName("IS_MASTER").withValue("true").endEnv()
                .addNewEnv().withName("MESSAGING_SERIALIZER").withValue(windupResource.getSpec().getMessaging_serializer()).endEnv()
                .addNewEnv().withName("DB_SERVICE_PREFIX_MAPPING").withValue(windupResource.getSpec().getApplication_name() + "-postgresql=DB").endEnv()
                .addNewEnv().withName("DB_JNDI").withValue(windupResource.getSpec().getDb_jndi()).endEnv()
                .addNewEnv().withName("DB_USERNAME").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getDb_username(),"user" + RandomStringUtils.randomAlphanumeric(3))).endEnv()
                .addNewEnv().withName("DB_PASSWORD").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getDb_password(),RandomStringUtils.randomAlphanumeric(8))).endEnv()
                .addNewEnv().withName("DB_DATABASE").withValue(windupResource.getSpec().getDb_database()).endEnv()
                .addNewEnv().withName("TX_DATABASE_PREFIX_MAPPING").withValue(windupResource.getSpec().getApplication_name() + "-postgresql=DB").endEnv()
                .addNewEnv().withName("DB_MIN_POOL_SIZE").withValue(windupResource.getSpec().getDb_min_pool_size()).endEnv()
                .addNewEnv().withName("DB_MAX_POOL_SIZE").withValue(windupResource.getSpec().getDb_max_pool_size()).endEnv()
                .addNewEnv().withName("DB_TX_ISOLATION").withValue(windupResource.getSpec().getDb_tx_isolation()).endEnv()
                .addNewEnv().withName("OPENSHIFT_KUBE_PING_LABELS").withValue("application=" + windupResource.getSpec().getApplication_name()).endEnv()
                .addNewEnv().withName("OPENSHIFT_KUBE_PING_NAMESPACE").withValue(namespace).endEnv()
                .addNewEnv().withName("HTTPS_KEYSTORE_DIR").withValue("/etc/eap-secret-volume").endEnv()
                .addNewEnv().withName("MQ_CLUSTER_PASSWORD").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getMq_cluster_password(),RandomStringUtils.randomAlphanumeric(8))).endEnv().addNewEnv().withName("MQ_QUEUES").withValue(windupResource.getSpec().getMq_queues()).endEnv()
                .addNewEnv().withName("MQ_TOPICS").withValue(windupResource.getSpec().getMq_topics()).endEnv()
                .addNewEnv().withName("JGROUPS_ENCRYPT_SECRET").withValue(windupResource.getSpec().getJgroups_encrypt_secret()).endEnv()
                .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE_DIR").withValue("/etc/jgroups-encrypt-secret-volume").endEnv()
                .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE").withValue(windupResource.getSpec().getJgroups_encrypt_keystore()).endEnv()
                .addNewEnv().withName("JGROUPS_ENCRYPT_NAME").withValue(windupResource.getSpec().getJgroups_encrypt_name()).endEnv()
                .addNewEnv().withName("JGROUPS_ENCRYPT_PASSWORD").withValue(windupResource.getSpec().getJgroups_encrypt_password()).endEnv()
                .addNewEnv().withName("JGROUPS_CLUSTER_PASSWORD").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getJgroups_cluster_password(),RandomStringUtils.randomAlphanumeric(8))).endEnv()
                .addNewEnv().withName("AUTO_DEPLOY_EXPLODED").withValue(windupResource.getSpec().getAuto_deploy_exploded()).endEnv()
                .addNewEnv().withName("DEFAULT_JOB_REPOSITORY").withValue(deployment_postgre).endEnv()
                .addNewEnv().withName("TIMER_SERVICE_DATA_STORE").withValue(deployment_postgre).endEnv()
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
                .addNewEnv().withName("SSO_SECRET").withValue(StringUtils.defaultIfBlank(windupResource.getSpec().getSso_secret(),RandomStringUtils.randomAlphanumeric(8))).endEnv()
                .addNewEnv().withName("SSO_ENABLE_CORS").withValue(windupResource.getSpec().getSso_enable_cors()).endEnv()
                .addNewEnv().withName("SSO_SAML_logOUT_PAGE").withValue(windupResource.getSpec().getSso_saml_logout_page()).endEnv()
                .addNewEnv().withName("SSO_DISABLE_SSL_CERTIFICATE_VALIDATION").withValue(windupResource.getSpec().getSso_disable_ssl_certificate_validation()).endEnv()
                .addNewEnv().withName("SSO_TRUSTSTORE").withValue(windupResource.getSpec().getSso_truststore()).endEnv()
                .addNewEnv().withName("SSO_TRUSTSTORE_DIR").withValue("/etc/sso-secret-volume").endEnv()
                .addNewEnv().withName("SSO_TRUSTSTORE_PASSWORD").withValue(windupResource.getSpec().getSso_truststore_password()).endEnv()
                .addNewEnv().withName("GC_MAX_METASPACE_SIZE").withValue(windupResource.getSpec().getGc_max_metaspace_size()).endEnv()
                .addNewEnv().withName("MAX_POST_SIZE").withValue(windupResource.getSpec().getMax_post_size()).endEnv()
              .endContainer()
              .addNewVolume()
                .withName(volume_mtaweb)
                .withNewPersistentVolumeClaim()
                  .withClaimName(volume_mtaweb_claim)
                .endPersistentVolumeClaim()
              .endVolume()
              .addNewVolume()
                .withName(volume_mtaweb_data)
                .withNewEmptyDir().endEmptyDir()
              .endVolume()
            .endSpec()
          .endTemplate()
        .endSpec().build();
    log.info("Created Deployment windup-web");

    Deployment deploymentExecutor = new DeploymentBuilder()
        .withNewMetadata()
          .withName(deployment_executor)
          .withLabels(getLabels())
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .withReplicas(1)
          .withNewSelector()
            .addToMatchLabels("deploymentConfig", deployment_executor)
          .endSelector()
          .withNewStrategy()
            .withType("Recreate")
          .endStrategy()
          .withNewTemplate()
            .withNewMetadata()
              .addToLabels("deploymentConfig", deployment_executor)
              .addToLabels("application", deployment_executor)
              .withName(deployment_executor)
            .endMetadata()
            .withNewSpec()
              .withServiceAccount(serviceAccount)
              .withTerminationGracePeriodSeconds(75L)
              .addNewContainer()
                .withName(deployment_executor)
                .withImage(getContainerImageName(windupResource.getSpec().getDocker_image_executor()))
                .withNewImagePullPolicy("Always")
                .withNewResources()
                  .addToRequests(Map.of("cpu", new Quantity(windupResource.getSpec().getExecutor_cpu_request())))
                  .addToRequests(Map.of("memory", new Quantity(windupResource.getSpec().getExecutor_mem_request())))
                  .addToLimits(Map.of("cpu", new Quantity(windupResource.getSpec().getExecutor_cpu_limit())))
                  .addToLimits(Map.of("memory", new Quantity(windupResource.getSpec().getExecutor_mem_limit())))
                .endResources()
                .addToVolumeMounts(new VolumeMountBuilder()
                  .withName(windupResource.getSpec().getApplication_name() + "-mta-web-executor-volume")
                  .withMountPath("/opt/eap/standalone/data")
                  .withReadOnly(false).build())
                .withNewLifecycle()
                  .withNewPreStop()
                    .withNewExec()
                      .withCommand("/opt/mta-cli/bin/stop.sh")
                    .endExec()
                  .endPreStop()
                .endLifecycle()
                .withNewLivenessProbe()
                  .withNewExec()
                    .withCommand(Arrays.stream(windupResource.getSpec().getExecutor_liveness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "/opt/mta-cli/bin/livenessProbe.sh")
                  .endExec()
                  .withInitialDelaySeconds(120)
                  .withFailureThreshold(3)
                  .withSuccessThreshold(1)
                  .withTimeoutSeconds(10)
                .endLivenessProbe()
                .withNewReadinessProbe()
                  .withNewExec()
                    .withCommand(Arrays.stream(windupResource.getSpec().getExecutor_readiness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "/opt/mta-cli/bin/livenessProbe.sh")
                  .endExec()
                  .withInitialDelaySeconds(120)
                  .withFailureThreshold(3)
                  .withSuccessThreshold(1)
                  .withTimeoutSeconds(10)
                .endReadinessProbe()
                .addNewEnv().withName("IS_MASTER").withValue("false").endEnv()
                .addNewEnv().withName("MESSAGING_SERIALIZER").withValue(windupResource.getSpec().getMessaging_serializer()).endEnv()
                .addNewEnv().withName("MESSAGING_HOST_VAR").withValue(windupResource.getSpec().getApplication_name() + "_SERVICE_HOST").endEnv()
                .addNewEnv().withName("MESSAGING_PASSWORD").withValue("gthudfal").endEnv()
                .addNewEnv().withName("MESSAGING_USER").withValue("jms-user").endEnv()
              .endContainer()
              .addNewVolume()
                .withName(volume_executor)
                .withNewEmptyDir().endEmptyDir()
              .endVolume()
            .endSpec()
          .endTemplate()
        .endSpec().build();
    log.info("Created Deployment for executor");

    Deployment deploymentPostgre = new DeploymentBuilder()
        .withNewMetadata()
          .withName(deployment_postgre)
          .addToLabels("application", windupResource.getSpec().getApplication_name())
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .withReplicas(1)
          .withNewSelector()
            .addToMatchLabels("deploymentConfig", deployment_postgre)
          .endSelector()
          .withNewStrategy()
            .withType("Recreate")
          .endStrategy()
          .withNewTemplate()
            .withNewMetadata()
              .addToLabels("deploymentConfig", deployment_postgre)
              .addToLabels("application", deployment_postgre)
              .withName(windupResource.getSpec().getApplication_name())
            .endMetadata()
            .withNewSpec()
              .withServiceAccount(serviceAccount)
              .withTerminationGracePeriodSeconds(60L)
              .addToVolumes(new VolumeBuilder()
                  .withName(volume_postgresql)
                  .withNewPersistentVolumeClaim()
                    .withClaimName(volume_postgresql_claim)
                  .endPersistentVolumeClaim().build())
              .addNewContainer()
                .withName(deployment_postgre)
                .withImage(windupResource.getSpec().getPostgresql_image())
                .withNewImagePullPolicy("Always")
                .withNewResources()
                  .addToRequests(Map.of("cpu", new Quantity(windupResource.getSpec().getPostgresql_cpu_request())))
                  .addToRequests(Map.of("memory", new Quantity(windupResource.getSpec().getPostgresql_mem_request())))
                  .addToLimits(Map.of("cpu", new Quantity(windupResource.getSpec().getPostgresql_cpu_limit())))
                  .addToLimits(Map.of("memory", new Quantity(windupResource.getSpec().getPostgresql_mem_limit())))
                .endResources()
                .addToVolumeMounts(new VolumeMountBuilder()
                    .withName(volume_postgresql)
                    .withMountPath("/var/lib/pgsql/data")
                    .withReadOnly(false).build())
                .addNewEnv().withName("POSTGRESQL_USER").withValue(windupResource.getSpec().getDb_username()).endEnv()
                .addNewEnv().withName("POSTGRESQL_PASSWORD").withValue(windupResource.getSpec().getDb_password()).endEnv()
                .addNewEnv().withName("POSTGRESQL_DATABASE").withValue(windupResource.getSpec().getDb_database()).endEnv()
                .addNewEnv().withName("POSTGRESQL_MAX_CONNECTIONS").withValue(windupResource.getSpec().getPostgresql_max_connections()).endEnv()
                .addNewEnv().withName("POSTGRESQL_MAX_PREPARED_TRANSACTIONS").withValue(windupResource.getSpec().getPostgresql_max_connections()).endEnv()
                .addNewEnv().withName("POSTGRESQL_SHARED_BUFFERS").withValue(windupResource.getSpec().getPostgresql_shared_buffers()).endEnv()
              .endContainer()
            .endSpec()
          .endTemplate()
        .endSpec().build();
    log.info("Created Deployment for PostgreSQL");

    return List.of(deploymentMTAweb, deploymentExecutor, deploymentPostgre);
  }
  //@format:on

  private String getContainerImageName(String containerImage) {
    return windupResource.getSpec().getDocker_images_repository() + "/" +
           ((!windupResource.getSpec().getDocker_images_user().isBlank()) ? windupResource.getSpec().getDocker_images_user() + "/" : "") +
           containerImage + ":" +
           windupResource.getSpec().getDocker_images_tag();
  }




}