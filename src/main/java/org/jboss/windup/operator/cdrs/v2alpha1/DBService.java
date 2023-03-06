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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.jboss.windup.operator.Constants;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

@KubernetesDependent(resourceDiscriminator = DBServiceDiscriminator.class)
@ApplicationScoped
public class DBService extends CRUDKubernetesDependentResource<Service, Windup> {

    public DBService() {
        super(Service.class);
    }

    @Override
    public Service desired(Windup cr, Context<Windup> context) {
        return newService(cr, context);
    }

    @SuppressWarnings("unchecked")
    private Service newService(Windup cr, Context<Windup> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new ServiceBuilder()
                .withNewMetadata()
                .withName(getServiceName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .endMetadata()
                .withSpec(getServiceSpec(cr))
                .build();
    }

    private ServiceSpec getServiceSpec(Windup cr) {
        return new ServiceSpecBuilder()
                .addNewPort()
                .withPort(5432)
                .withProtocol(Constants.SERVICE_PROTOCOL)
                .endPort()
                .withSelector(Constants.DB_SELECTOR_LABELS)
                .withType("ClusterIP")
                .build();
    }

    public static String getServiceName(Windup cr) {
        return cr.getMetadata().getName() + Constants.DB_SERVICE_SUFFIX;
    }

}
