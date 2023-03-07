package org.jboss.windup.operator;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppServerConfig {
    private String[] webLivenessProbeCmd;
    private String[] webReadinessProbeCmd;
}
