package org.jboss.windup.operator.controller;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Windup {
    private AppServerType appServerType;

    public enum AppServerType {
        wildfly,
        eap
    }
}
