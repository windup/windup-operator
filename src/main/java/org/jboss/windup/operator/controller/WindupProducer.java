package org.jboss.windup.operator.controller;

import io.quarkus.arc.properties.IfBuildProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

@Dependent
public class WindupProducer {

    private static final String APPLICATION_SERVER_PROPERTY_NAME = "app-base-server";

    @Produces
    @ApplicationScoped
    @IfBuildProperty(name = APPLICATION_SERVER_PROPERTY_NAME, stringValue = "wildfly", enableIfMissing = true)
    public Windup wildfly() {
        return Windup.builder()
                .appServerType(Windup.AppServerType.wildfly)
                .build();
    }

    @Produces
    @ApplicationScoped
    @IfBuildProperty(name = APPLICATION_SERVER_PROPERTY_NAME, stringValue = "eap")
    public Windup eap() {
        return Windup.builder()
                .appServerType(Windup.AppServerType.eap)
                .build();
    }

}
