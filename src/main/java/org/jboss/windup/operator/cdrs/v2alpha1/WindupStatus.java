package org.jboss.windup.operator.cdrs.v2alpha1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WindupStatus {
    private List<WindupStatusCondition> conditions;
}
