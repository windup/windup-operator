package org.jboss.windup.operator.util;

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
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.windup.operator.controller.Windup;
import org.jboss.windup.operator.model.WindupResource;
import org.jboss.windup.operator.model.WindupResourceList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
public class WindupDeployment {
  public static final  String APPLICATION = "application";
  public static final  String APP = "app";
  public static final  String CREATEDBY = "created-by";

  public static final String WINDUP_OPERATOR = "windup-operator";
  public static final String CREATED_BY = "created-by";

  MixedOperation<WindupResource, WindupResourceList, Resource<WindupResource>> crClient;

  KubernetesClient k8sClient;

  private WindupResource windupResource;

  private String deployment_executor;
  private String deployment_postgre;
  private String volume_postgresql_claim;
  private String volume_windup_web_claim;
  private String volume_postgresql;
  private String volume_executor;
  private String volume_windup_web;
  private String volume_windup_web_data;

  private String mq_cluster_password;
  private String db_username;
  private String db_password;
  private String sso_secret;
  private String jgroups_cluster_password;

  private String application_name;

  private String deployment_amq;

  private String namespace;

  private String serviceAccount;

  private String sso_public_key;
  private String ssoPublicKeyDefault;
  private Integer executor_desired_replicas;
  private Windup windup;

  public WindupDeployment(WindupResource windupResource, MixedOperation<WindupResource, WindupResourceList, Resource<WindupResource>> crClient, 
                          KubernetesClient k8sClient, String namespace, 
                          String serviceAccount, String ssoPublicKeyDefault,
                          Windup windup) {
    this.windupResource = windupResource;
    this.crClient = crClient;
    this.k8sClient = k8sClient;
    this.namespace = namespace;
    this.serviceAccount = serviceAccount;
    this.ssoPublicKeyDefault = ssoPublicKeyDefault;
    this.windup = windup;
    initParams();
  }

  private void initParams() {
    // Init names of objects
    application_name = windupResource.getMetadata().getName();
    volume_postgresql_claim = application_name + "-postgresql-claim";
    deployment_postgre = application_name + "-postgresql";
    deployment_executor = application_name + "-executor";
    volume_windup_web_claim = application_name + "-windup-web-claim";
    volume_postgresql = application_name + "-postgresql-pvol";
    volume_executor = application_name + "-windup-web-executor-volume";
    volume_windup_web = application_name + "-windup-web-pvol";
    volume_windup_web_data = application_name + "-windup-web-pvol-data";
    deployment_amq = application_name + "-amq";

    // Calculate values if they come blank
    mq_cluster_password =  StringUtils.defaultIfBlank(windupResource.getSpec().getMq_cluster_password(),RandomStringUtils.randomAlphanumeric(8));
    db_username =  StringUtils.defaultIfBlank(windupResource.getSpec().getDb_username(),"user" + RandomStringUtils.randomAlphanumeric(3));
    db_password =  StringUtils.defaultIfBlank(windupResource.getSpec().getDb_password(),RandomStringUtils.randomAlphanumeric(8));
    sso_secret = StringUtils.defaultIfBlank(windupResource.getSpec().getSso_secret(),RandomStringUtils.randomAlphanumeric(8));
    jgroups_cluster_password  = StringUtils.defaultIfBlank(windupResource.getSpec().getJgroups_cluster_password(),RandomStringUtils.randomAlphanumeric(8));
    sso_public_key = StringUtils.defaultIfBlank(windupResource.getSpec().getSso_public_key(), ssoPublicKeyDefault);
    executor_desired_replicas = ObjectUtils.defaultIfNull(windupResource.getSpec().getExecutor_desired_replicas(), 1);
  }

  public void deploy() {
    // We are adding one by one instead of createOrReplace(volumes.toArray(new Volume[2])) 
    // because in that case we receive an error : Too Many Items to Create
    initCRStatusOnDeployment();

    createVolumes().stream().forEach(e -> k8sClient.persistentVolumeClaims().inNamespace(namespace).createOrReplace(e));

    createDeployment().stream().forEach(e -> k8sClient.apps().deployments().inNamespace(namespace).createOrReplace(e));

    createServices().stream().forEach(e -> k8sClient.services().inNamespace(namespace).createOrReplace(e));

    createIngresses().stream().forEach(e -> k8sClient.network().v1().ingresses().inNamespace(namespace).createOrReplace(e));
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
    Service windupWebConsoleSvc = new ServiceBuilder()
        .withApiVersion("v1")
        .withNewMetadata()
          .withName(application_name)
          .withLabels(getLabels())
          .addToAnnotations("description", "The web server's http port")
          .addToAnnotations("service.alpha.openshift.io/dependencies","[{\"name\": \"" + deployment_postgre + "\", \"kind\": \"Service\"}]")
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .addNewPort()
            .withName("web-port")
            .withPort(8080)
            .withTargetPort(new IntOrString(8080))
          .endPort()
          .withSelector(Collections.singletonMap("deploymentConfig", application_name))
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
          .withName(application_name + "-amq")
          .withLabels(getLabels())
          .addToAnnotations("description", "Windup Master AMQ port.")
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .addNewPort()
            .withName("amq-port")
            .withPort(61616)
            .withTargetPort(new IntOrString(61616))
          .endPort()
          .withSelector(Collections.singletonMap("deploymentConfig",deployment_amq))
        .endSpec().build();
    log.info("Created Service for AMQ");

    return List.of(windupWebConsoleSvc, postgreSvc, amqSvc);
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
        APPLICATION, application_name,
        APP, application_name,
        CREATEDBY, WINDUP_OPERATOR);
  }

  // Checking the cluster domain on Openshift
  @SuppressWarnings("unchecked")
  private String getClusterDomainOnOpenshift() {
    String clusterDomain = ""; 
    try {
      CustomResourceDefinitionContext customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
      .withName("Ingress")
      .withGroup("config.openshift.io")
      .withVersion("v1")
      .withPlural("ingresses")
      .withScope("Cluster")
      .build();
      Map<String, Object> clusterObject = k8sClient.customResource(customResourceDefinitionContext).get("cluster");
      Map<String,String> objectSpec = (Map<String,String>) clusterObject.get("spec");
      clusterDomain = objectSpec.get("domain");

    } catch (KubernetesClientException exception) {
      log.log(Level.WARNING, "You are probably not on Openshift", exception);
    }

    return clusterDomain;
  }

  private List<Ingress> createIngresses() {
    List<Ingress> ingresses = new ArrayList<>();
    String hostnameHttp = windupResource.getSpec().getHostname_http();

    // if the user doesn't provide hostname we'll try to discover it on Openshift
    // if we are in K8s then cluster domain will be blank
    if (StringUtils.isBlank(hostnameHttp)) {
      hostnameHttp = getClusterDomainOnOpenshift();
      log.info("Cluster Domain : [" + hostnameHttp + "]");
    }

    log.info("Adding the 2 Ingresses ");
    ingresses.add(createWebConsoleHttpsIngress(hostnameHttp));
    ingresses.add(createWebConsoleHttpIngress(hostnameHttp));

    return ingresses;
  }

  private Ingress createWebConsoleHttpIngress(String hostnameHttp) {
    Ingress ingressObject = new IngressBuilder()
                .withNewMetadata()
                  .withName(application_name)
                  .withLabels(getLabels())
                  .addToAnnotations("description", "Route for application's http service.")
                  .addToAnnotations("console.alpha.openshift.io/overview-app-route", "true")
                  .withOwnerReferences(getOwnerReference())
                .endMetadata()
                .withNewSpec()
                  .addNewRule()
                    .withNewHttp()
                      .addNewPath()
                        .withPath("/")
                        .withPathType("Prefix")
                          .withNewBackend()
                            .withNewService()
                              .withNewName(application_name)
                              .withNewPort()
                                .withNumber(8080)
                              .endPort()
                            .endService()
                          .endBackend()
                        .endPath()
                    .endHttp()
                  .endRule()
                .endSpec().build();
    if (StringUtils.isNotBlank(hostnameHttp)) {
      ingressObject.getSpec().getRules().get(0).setHost(namespace + "-" + application_name + "." + hostnameHttp);
    }
    return ingressObject;
  }

  private Ingress createWebConsoleHttpsIngress(String hostnameHttp) {
    String hostHTTPS = "secure-" + namespace + "-" + application_name + "." + hostnameHttp;

    // We will use the same HTTP ingress but we'll add what's needed for HTTPS
    Ingress ingress = createWebConsoleHttpIngress(hostnameHttp);
    ingress.getMetadata().setName("secure-" + application_name);
    ingress.getMetadata().getAnnotations().remove("console.alpha.openshift.io/overview-app-route");

    IngressTLS ingressTLS = new IngressTLSBuilder().build();

    // Only set the host and the secret if we receive a secret
    // Otherwise an empty array for tls will allow OCP 4.6 to create a TLS Route with default cert
    if (StringUtils.isNotBlank(windupResource.getSpec().getTls_secret())) {
      if (StringUtils.isNotBlank(hostnameHttp)) {
        ingressTLS.setHosts(List.of(hostHTTPS));
      }
      ingressTLS.setSecretName(windupResource.getSpec().getTls_secret());
    }
    ingress.getSpec().setTls(Collections.singletonList(ingressTLS));

    if (StringUtils.isNotBlank(hostnameHttp)) {
      ingress.getSpec().getRules().get(0).setHost(hostHTTPS);
    }

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

    PersistentVolumeClaim windupPersistentVolumeClaim = new PersistentVolumeClaimBuilder()
        .withNewMetadata()
          .withName(volume_windup_web_claim)
          .withLabels(getLabels())
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .withAccessModes("ReadWriteOnce")
          .withNewResources()
            .addToRequests("storage", new Quantity(windupResource.getSpec().getWindup_Volume_Capacity()))
          .endResources()
        .endSpec().build();
    log.info("Created PVC for windup");

    return List.of(postgrPersistentVolumeClaim, windupPersistentVolumeClaim);
  }

  private List<Deployment> createDeployment() {

    Deployment deploymentExecutor = deploymentExecutor();

    Deployment deploymentPostgre = deploymentPostgre();

    Deployment deploymentWindupWeb = deploymentWeb();

    return List.of(deploymentWindupWeb, deploymentExecutor, deploymentPostgre);
  }
  //@format:on

  private Deployment deploymentWeb() {
    Deployment deploymentWindupWeb = new DeploymentBuilder()
    .withNewMetadata()
      .withName(application_name)
      .withLabels(getLabels())
      .withOwnerReferences(getOwnerReference())
    .endMetadata()
    .withNewSpec()
      .withReplicas(1)
      .withNewSelector()
        .addToMatchLabels("deploymentConfig", application_name)
      .endSelector()
      .withNewStrategy()
        .withType("Recreate")
      .endStrategy()
      .withNewTemplate()
        .withNewMetadata()
          .withLabels(getLabels())
          .addToLabels("deploymentConfig", application_name)
          .withName(application_name)
        .endMetadata()
        .withNewSpec()
          .withServiceAccount(serviceAccount).withTerminationGracePeriodSeconds(75L)
          .addNewContainer()
            .withName(application_name)
            .withImage(getContainerImageName( windupResource.getSpec().getDocker_image_web()))
            .withNewImagePullPolicy("Always")
            .withNewResources()
              .addToRequests(Map.of("cpu", new Quantity(windupResource.getSpec().getWeb_cpu_request())))
              .addToRequests(Map.of("memory", new Quantity(windupResource.getSpec().getWeb_mem_request())))
              .addToLimits(Map.of("cpu", new Quantity(windupResource.getSpec().getWeb_cpu_limit())))
              .addToLimits(Map.of("memory", new Quantity(windupResource.getSpec().getWeb_mem_limit())))
            .endResources()
            .addToVolumeMounts(new VolumeMountBuilder()
                                .withName(volume_windup_web)
                                .withMountPath(String.format("/opt/%s/standalone/data/windup", windup.getAppServerType()))
                                .withReadOnly(false)
                                .build())
            .addToVolumeMounts(new VolumeMountBuilder()
                                .withName(volume_windup_web_data)
                                .withMountPath(String.format("/opt/%s/standalone/data", windup.getAppServerType()))
                                .withReadOnly(false)
                                .build())
            .withNewLifecycle()
              .withNewPreStop()
                .withNewExec()
                  .withCommand("${JBOSS_HOME}/bin/jboss-cli.sh", "-c", ":shutdown(timeout=60)")
                .endExec()
              .endPreStop()
            .endLifecycle()
            .withNewLivenessProbe()
              .withNewExec()
                .withCommand(Arrays.stream(windupResource.getSpec().getWeb_liveness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "${JBOSS_HOME}/bin/jboss-cli.sh --connect --commands=ls | grep 'server-state=running'")
              .endExec()
              .withInitialDelaySeconds(Integer.parseInt(windupResource.getSpec().getWebLivenessInitialDelaySeconds()))
              .withFailureThreshold(Integer.parseInt(windupResource.getSpec().getWebLivenessFailureThreshold()))
              .withSuccessThreshold(1)
              .withTimeoutSeconds(Integer.parseInt(windupResource.getSpec().getWebLivenessTimeoutSeconds()))
            .endLivenessProbe()
            .withNewReadinessProbe()
              .withNewExec()
                .withCommand(Arrays.stream(windupResource.getSpec().getWeb_readiness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "${JBOSS_HOME}/bin/jboss-cli.sh --connect --commands='ls deployment' | grep 'api.war'")
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
            .addNewEnv().withName("DB_SERVICE_PREFIX_MAPPING").withValue(application_name + "-postgresql=DB").endEnv()
            .addNewEnv().withName("DB_JNDI").withValue(windupResource.getSpec().getDb_jndi()).endEnv()
            .addNewEnv().withName("DB_USERNAME").withValue(db_username).endEnv()
            .addNewEnv().withName("DB_PASSWORD").withValue(db_password).endEnv()
            .addNewEnv().withName("DB_DATABASE").withValue(windupResource.getSpec().getDb_database()).endEnv()
            .addNewEnv().withName("TX_DATABASE_PREFIX_MAPPING").withValue(application_name + "-postgresql=DB").endEnv()
            .addNewEnv().withName("DB_MIN_POOL_SIZE").withValue(windupResource.getSpec().getDb_min_pool_size()).endEnv()
            .addNewEnv().withName("DB_MAX_POOL_SIZE").withValue(windupResource.getSpec().getDb_max_pool_size()).endEnv()
            .addNewEnv().withName("DB_TX_ISOLATION").withValue(windupResource.getSpec().getDb_tx_isolation()).endEnv()
            .addNewEnv().withName("OPENSHIFT_KUBE_PING_LABELS").withValue("application=" + application_name).endEnv()
            .addNewEnv().withName("OPENSHIFT_KUBE_PING_NAMESPACE").withValue(namespace).endEnv()
            .addNewEnv().withName("HTTPS_KEYSTORE_DIR").withValue("/etc/wildfly-secret-volume").endEnv()
            .addNewEnv().withName("MQ_CLUSTER_PASSWORD").withValue(mq_cluster_password).endEnv()
            .addNewEnv().withName("MQ_QUEUES").withValue(windupResource.getSpec().getMq_queues()).endEnv()
            .addNewEnv().withName("MQ_TOPICS").withValue(windupResource.getSpec().getMq_topics()).endEnv()
            .addNewEnv().withName("JGROUPS_ENCRYPT_SECRET").withValue(windupResource.getSpec().getJgroups_encrypt_secret()).endEnv()
            .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE_DIR").withValue("/etc/jgroups-encrypt-secret-volume").endEnv()
            .addNewEnv().withName("JGROUPS_ENCRYPT_KEYSTORE").withValue(windupResource.getSpec().getJgroups_encrypt_keystore()).endEnv()
            .addNewEnv().withName("JGROUPS_ENCRYPT_NAME").withValue(windupResource.getSpec().getJgroups_encrypt_name()).endEnv()
            .addNewEnv().withName("JGROUPS_ENCRYPT_PASSWORD").withValue(windupResource.getSpec().getJgroups_encrypt_password()).endEnv()
            .addNewEnv().withName("JGROUPS_CLUSTER_PASSWORD").withValue(jgroups_cluster_password).endEnv()
            .addNewEnv().withName("AUTO_DEPLOY_EXPLODED").withValue(windupResource.getSpec().getAuto_deploy_exploded()).endEnv()
            .addNewEnv().withName("DEFAULT_JOB_REPOSITORY").withValue(deployment_postgre).endEnv()
            .addNewEnv().withName("TIMER_SERVICE_DATA_STORE").withValue(deployment_postgre).endEnv()
            .addNewEnv().withName("SSO_URL").withValue(windupResource.getSpec().getSso_url()).endEnv()
            .addNewEnv().withName("SSO_SERVICE_URL").withValue(windupResource.getSpec().getSso_service_url()).endEnv()
            .addNewEnv().withName("SSO_REALM").withValue(windupResource.getSpec().getSso_realm()).endEnv()
            .addNewEnv().withName("SSO_USERNAME").withValue(windupResource.getSpec().getSso_username()).endEnv()
            .addNewEnv().withName("SSO_PASSWORD").withValue(windupResource.getSpec().getSso_password()).endEnv()
            .addNewEnv().withName("SSO_PUBLIC_KEY").withValue(sso_public_key).endEnv()
            .addNewEnv().withName("SSO_BEARER_ONLY").withValue(windupResource.getSpec().getSso_bearer_only()).endEnv()
            .addNewEnv().withName("SSO_SAML_KEYSTORE_SECRET").withValue(windupResource.getSpec().getSso_saml_keystore_secret()).endEnv()
            .addNewEnv().withName("SSO_SAML_KEYSTORE").withValue(windupResource.getSpec().getSso_saml_keystore()).endEnv()
            .addNewEnv().withName("SSO_SAML_KEYSTORE_DIR").withValue("/etc/sso-saml-secret-volume").endEnv()
            .addNewEnv().withName("SSO_SAML_CERTIFICATE_NAME").withValue(windupResource.getSpec().getSso_saml_certificate_name()).endEnv()
            .addNewEnv().withName("SSO_SAML_KEYSTORE_PASSWORD").withValue(windupResource.getSpec().getSso_saml_keystore_password()).endEnv()
            .addNewEnv().withName("SSO_SECRET").withValue(sso_secret).endEnv()
            .addNewEnv().withName("SSO_ENABLE_CORS").withValue(windupResource.getSpec().getSso_enable_cors()).endEnv()
            .addNewEnv().withName("SSO_SAML_logOUT_PAGE").withValue(windupResource.getSpec().getSso_saml_logout_page()).endEnv()
            .addNewEnv().withName("SSO_DISABLE_SSL_CERTIFICATE_VALIDATION").withValue(windupResource.getSpec().getSso_disable_ssl_certificate_validation()).endEnv()
            .addNewEnv().withName("SSO_TRUSTSTORE").withValue(windupResource.getSpec().getSso_truststore()).endEnv()
            .addNewEnv().withName("SSO_TRUSTSTORE_DIR").withValue("/etc/sso-secret-volume").endEnv()
            .addNewEnv().withName("SSO_TRUSTSTORE_PASSWORD").withValue(windupResource.getSpec().getSso_truststore_password()).endEnv()
            .addNewEnv().withName("GC_MAX_METASPACE_SIZE").withValue(windupResource.getSpec().getGc_max_metaspace_size()).endEnv()
            .addNewEnv().withName("MAX_POST_SIZE").withValue(windupResource.getSpec().getMax_post_size()).endEnv()
            .addNewEnv().withName("SSO_FORCE_LEGACY_SECURITY").withValue(windupResource.getSpec().getSso_force_legacy_security()).endEnv()
          .endContainer()
          .addNewVolume()
            .withName(volume_windup_web)
            .withNewPersistentVolumeClaim()
              .withClaimName(volume_windup_web_claim)
            .endPersistentVolumeClaim()
          .endVolume()
          .addNewVolume()
            .withName(volume_windup_web_data)
            .withNewEmptyDir().endEmptyDir()
          .endVolume()
        .endSpec()
      .endTemplate()
    .endSpec().build();

    log.info("Created Deployment windup-web");

    return deploymentWindupWeb;
  }

  private Deployment deploymentPostgre() {
    Deployment deploymentPostgre = new DeploymentBuilder()
        .withNewMetadata()
          .withName(deployment_postgre)
          .withLabels(getLabels())
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
              .addToLabels("application", application_name)
              .withName(deployment_postgre)
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
                .addNewEnv().withName("POSTGRESQL_USER").withValue(db_username).endEnv()
                .addNewEnv().withName("POSTGRESQL_PASSWORD").withValue(db_password).endEnv()
                .addNewEnv().withName("POSTGRESQL_DATABASE").withValue(windupResource.getSpec().getDb_database()).endEnv()
                .addNewEnv().withName("POSTGRESQL_MAX_CONNECTIONS").withValue(windupResource.getSpec().getPostgresql_max_connections()).endEnv()
                .addNewEnv().withName("POSTGRESQL_MAX_PREPARED_TRANSACTIONS").withValue(windupResource.getSpec().getPostgresql_max_connections()).endEnv()
                .addNewEnv().withName("POSTGRESQL_SHARED_BUFFERS").withValue(windupResource.getSpec().getPostgresql_shared_buffers()).endEnv()
              .endContainer()
            .endSpec()
          .endTemplate()
        .endSpec().build();
    log.info("Created Deployment for PostgreSQL");
    return deploymentPostgre;
  }

  private Deployment deploymentExecutor() {
    Deployment deploymentExecutor = new DeploymentBuilder()
        .withNewMetadata()
          .withName(deployment_executor)
          .withLabels(getLabels())
          .withOwnerReferences(getOwnerReference())
        .endMetadata()
        .withNewSpec()
          .withReplicas(windupResource.getSpec().getExecutor_desired_replicas())
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
                  .withName(application_name + "-windup-web-executor-volume")
                  .withMountPath("/opt/wildfly/standalone/data")
                  .withReadOnly(false).build())
                .withNewLifecycle()
                  .withNewPreStop()
                    .withNewExec()
                      .withCommand("/opt/windup-cli/bin/stop.sh")
                    .endExec()
                  .endPreStop()
                .endLifecycle()
                .withNewLivenessProbe()
                  .withNewExec()
                    .withCommand(Arrays.stream(windupResource.getSpec().getExecutor_liveness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "/opt/windup-cli/bin/livenessProbe.sh")
                  .endExec()
                  .withInitialDelaySeconds(120)
                  .withFailureThreshold(3)
                  .withSuccessThreshold(1)
                  .withTimeoutSeconds(10)
                .endLivenessProbe()
                .withNewReadinessProbe()
                  .withNewExec()
                    .withCommand(Arrays.stream(windupResource.getSpec().getExecutor_readiness_probe().split(",")).map(String::trim).collect(Collectors.toList())) //"/bin/bash", "-c", "/opt/windup-cli/bin/livenessProbe.sh")
                  .endExec()
                  .withInitialDelaySeconds(120)
                  .withFailureThreshold(3)
                  .withSuccessThreshold(1)
                  .withTimeoutSeconds(10)
                .endReadinessProbe()
                .addNewEnv().withName("IS_MASTER").withValue("false").endEnv()
                .addNewEnv().withName("MESSAGING_SERIALIZER").withValue(windupResource.getSpec().getMessaging_serializer()).endEnv()
                .addNewEnv().withName("MESSAGING_HOST_VAR").withValue(application_name + "_SERVICE_HOST").endEnv()
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
    return deploymentExecutor;
  }

  private String getContainerImageName(String containerImage) {
    return windupResource.getSpec().getDocker_images_repository() + "/" +
           ((!windupResource.getSpec().getDocker_images_user().isBlank()) ? windupResource.getSpec().getDocker_images_user() + "/" : "") +
           containerImage + ":" +
           windupResource.getSpec().getDocker_images_tag();
  }

}