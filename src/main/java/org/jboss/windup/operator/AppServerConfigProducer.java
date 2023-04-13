package org.jboss.windup.operator;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.IfBuildProfile;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

@Dependent
public class AppServerConfigProducer {

    @Produces
    @DefaultBean
    public AppServerConfig wildflyConfig() {
        return AppServerConfig.builder()
                .webLivenessProbeCmd(new String[]{"/bin/sh", "-c", "${JBOSS_HOME}/bin/jboss-cli.sh --connect --commands='/core-service=management:read-boot-errors()' | grep '\"result\" => \\[]' && ${JBOSS_HOME}/bin/jboss-cli.sh --connect --commands=ls | grep 'server-state=running'"})
                .webReadinessProbeCmd(new String[]{"/bin/sh", "-c", "${JBOSS_HOME}/bin/jboss-cli.sh --connect --commands='/core-service=management:read-boot-errors()' | grep '\"result\" => \\[]' && ${JBOSS_HOME}/bin/jboss-cli.sh --connect --commands='ls deployment' | grep 'api.war'"})
                .build();
    }

    @Produces
    @IfBuildProfile("eap")
    public AppServerConfig eapConfig() {
        return AppServerConfig.builder()
                .webLivenessProbeCmd(new String[]{"/bin/sh", "-c", "/opt/eap/bin/livenessProbe.sh"})
                .webReadinessProbeCmd(new String[]{"/bin/sh", "-c", "/opt/eap/bin/readinessProbe.sh"})
                .build();
    }

}
