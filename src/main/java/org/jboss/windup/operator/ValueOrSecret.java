package org.jboss.windup.operator;

import io.fabric8.kubernetes.api.model.SecretKeySelector;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValueOrSecret {
    private String name;
    private String value;
    private SecretKeySelector secret;
}
