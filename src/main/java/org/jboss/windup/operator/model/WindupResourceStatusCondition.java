package org.jboss.windup.operator.model;

import io.fabric8.kubernetes.api.model.KubernetesResource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@Builder
@AllArgsConstructor
public class WindupResourceStatusCondition implements KubernetesResource {
    private static final long serialVersionUID = 1L;

    private String status;
    private String reason;
    private String message;
    private String type;
    private String lastTransitionTime;
}
