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
package org.jboss.windup.operator;

import java.util.Map;

public class Constants {
    public static final String CRDS_GROUP = "windup.jboss.org";
    public static final String CRDS_VERSION = "v1";

    public static final String CONTEXT_LABELS_KEY = "labels";
    public static final String CONTEXT_CONFIG_KEY = "config";
    public static final String CONTEXT_K8S_CLIENT_KEY = "k8sClient";

    //
    public static final String WINDUP_NAME = "windup";
    public static final String WINDUP_WEB_NAME = "windup-web";
    public static final String WINDUP_EXECUTOR_NAME = "windup-executor";
    public static final String WINDUP_DB_NAME = "postgresql";

    //
    public static final Map<String, String> DB_SELECTOR_LABELS = Map.of(
            "openubl-operator/group", "db"
    );
    public static final Map<String, String> WEB_SELECTOR_LABELS = Map.of(
            "openubl-operator/group", "web"
    );

    //
    public static final Integer HTTP_PORT = 8080;
    public static final Integer HTTPS_PORT = 8443;
    public static final String SERVICE_PROTOCOL = "TCP";

    //
    public static final String DB_PVC_SUFFIX = "-" + WINDUP_DB_NAME + "-pvc";
    public static final String DB_SECRET_SUFFIX = "-" + WINDUP_DB_NAME + "-secret";
    public static final String DB_DEPLOYMENT_SUFFIX = "-" + WINDUP_DB_NAME + "-deployment";
    public static final String DB_SERVICE_SUFFIX = "-" + WINDUP_DB_NAME + "-service";

    public static final String WEB_PVC_SUFFIX = "-" + WINDUP_WEB_NAME + "-pvc";
    public static final String WEB_DEPLOYMENT_SUFFIX = "-" + WINDUP_WEB_NAME + "-deployment";
    public static final String WEB_SERVICE_SUFFIX = "-" + WINDUP_WEB_NAME + "-service";

    public static final String EXECUTOR_DEPLOYMENT_SUFFIX = "-" + WINDUP_EXECUTOR_NAME + "-deployment";

    public static final String INGRESS_SUFFIX = "-" + WINDUP_WEB_NAME + "-ingress";

    //
    public static final String DB_SECRET_USERNAME = "username";
    public static final String DB_SECRET_PASSWORD = "password";
    public static final String DB_SECRET_DATABASE_NAME = "database";

    public static final String POSTGRESQL_PVC_SIZE = "10G";
}
