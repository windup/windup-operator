package org.jboss.windup.operator.cdrs.v2alpha1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WindupSpec {

    @JsonPropertyDescription("Number of instances of the executor pod. Default is 1.")
    private int executorInstances = 1;

    @JsonPropertyDescription("Size of the PVC where the reports will be stored")
    private String dataSize = "20G";

    @JsonPropertyDescription("Secret(s) that might be used when pulling an image from a private container image registry or repository.")
    private List<LocalObjectReference> imagePullSecrets;

    @JsonProperty("db")
    @JsonPropertyDescription("In this section you can find all properties related to connect to a database.")
    private DatabaseSpec databaseSpec;

    @JsonProperty("hostname")
    @JsonPropertyDescription("In this section you can configure hostname and related properties.")
    private HostnameSpec hostnameSpec;

    @JsonProperty("http")
    @JsonPropertyDescription("In this section you can configure Keycloak features related to HTTP and HTTPS")
    private HttpSpec httpSpec;

    @JsonProperty("sso")
    @JsonPropertyDescription("In this section you can configure SSO settings.")
    private SSOSpec ssoSpec;

    @JsonProperty("webResourceLimits")
    @JsonPropertyDescription("In this section you can configure resource limits settings for the Web Console.")
    private ResourcesLimitSpec webResourceLimitSpec;

    @JsonProperty("executorResourceLimits")
    @JsonPropertyDescription("In this section you can configure resource limits settings for the Executor.")
    private ResourcesLimitSpec executorResourceLimitSpec;

    @JsonProperty("jgroups")
    @JsonPropertyDescription("In this section you can configure JGroups settings.")
    private JGroupsSpec jgroupsSpec;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DatabaseSpec {
        @JsonPropertyDescription("Size of the PVC to create.")
        private String size;

        @JsonProperty("resourceLimits")
        @JsonPropertyDescription("In this section you can configure resource limits settings.")
        private ResourcesLimitSpec resourceLimitSpec;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HostnameSpec {
        @JsonPropertyDescription("Hostname for the server.")
        private String hostname;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HttpSpec {
        @JsonPropertyDescription("A secret containing the TLS configuration for HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.")
        private String tlsSecret;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SSOSpec {
        @JsonPropertyDescription("Server url.")
        private String serverUrl;

        @JsonPropertyDescription("Realm.")
        private String realm;

        @JsonPropertyDescription("SSL required property. Valid values are: 'ALL', 'EXTERNAL', 'NONE'.")
        private String sslRequired = "EXTERNAL";

        @JsonPropertyDescription("Client id.")
        private String clientId;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResourcesLimitSpec {
        @JsonPropertyDescription("Requested CPU.")
        private String cpuRequest;

        @JsonPropertyDescription("Limit CPU.")
        private String cpuLimit;

        @JsonPropertyDescription("Requested memory.")
        private String memoryRequest;

        @JsonPropertyDescription("Limit Memory.")
        private String memoryLimit;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JGroupsSpec {
        @JsonPropertyDescription("The name of the secret containing the keystore file")
        private String encryptSecret;

        @JsonPropertyDescription("The name of the keystore file within the secret")
        private String encryptKeystore;

        @JsonPropertyDescription("The name associated with the server certificate.")
        private String encryptName;

        @JsonPropertyDescription("The reference to a secret holding the password for the keystore and certificate.")
        private SecretKeySelector encryptPassword;

        @JsonPropertyDescription("JGroups cluster password.")
        private SecretKeySelector clusterPassword;
    }
}
