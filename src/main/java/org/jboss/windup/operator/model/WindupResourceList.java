package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.fabric8.kubernetes.client.CustomResourceList;

@JsonSerialize
public class WindupResourceList extends CustomResourceList<WindupResource> {

}

