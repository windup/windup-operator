package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Getter;
import lombok.Setter;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
public class WindupResourceSpec implements KubernetesResource {
    private static final long serialVersionUID = 1L;

    private String version;
    private String application_name;
    private String hostname_http;
    private String volumeCapacity;
    private String mta_Volume_Capacity;
    private String docker_images_repository;
    private String docker_images_user;
    private String docker_images_tag;
    private String docker_image_web;
    private String docker_image_executor;
    private String messaging_serializer;
    private String db_jndi;
    private String db_username;
    private String db_password;
    private String db_min_pool_size;
    private String db_max_pool_size;
    private String db_tx_isolation;
    private String mq_cluster_password;
    private String mq_queues;
    private String mq_topics;
    private String jgroups_encrypt_secret;
    private String jgroups_encrypt_keystore;
    private String jgroups_encrypt_name;
    private String jgroups_encrypt_password;
    private String jgroups_cluster_password;
    private String auto_deploy_exploded;
    private String sso_url;
    private String sso_service_url;
    private String sso_realm;
    private String sso_username;
    private String sso_password;
    private String sso_public_key;
    private String sso_bearer_only;
    private String sso_saml_keystore_secret;
    private String sso_saml_keystore;
    private String sso_saml_certificate_name;
    private String sso_saml_keystore_password;
    private String sso_secret;
    private String sso_enable_cors;
    private String sso_saml_logout_page;
    private String sso_disable_ssl_certificate_validation;
    private String sso_truststore;
    private String sso_truststore_password;
    private String sso_truststore_secret;
    private String gc_max_metaspace_size;
    private String max_post_size;
    private String sso_force_legacy_security;
    private String db_database;
    private String postgresql_max_connections;
    private String postgresql_shared_buffers;
    private String postgresql_cpu_request;
    private String postgresql_mem_request;
    private String postgresql_cpu_limit;
    private String postgresql_mem_limit;
    private String postgresql_image;
    private String webLivenessInitialDelaySeconds;
    private String webLivenessTimeoutSeconds;
    private String webLivenessFailureThreshold;
    private String webReadinessInitialDelaySeconds;
    private String webReadinessTimeoutSeconds;
    private String webReadinessFailureThreshold;
    private String web_cpu_request;
    private String web_mem_request;
    private String executor_cpu_request;
    private String executor_mem_request;
    private String executor_cpu_limit;
    private String executor_mem_limit;
    private String web_cpu_limit;
    private String web_mem_limit;
    private String web_readiness_probe;
    private String web_liveness_probe;
    private String executor_readiness_probe;
    private String executor_liveness_probe;
    private Integer executor_desired_replicas;
    private String tls_secret;
}

