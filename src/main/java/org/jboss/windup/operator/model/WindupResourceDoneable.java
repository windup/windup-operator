package org.jboss.windup.operator.model;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class WindupResourceDoneable extends CustomResourceDoneable<WindupResource> {

    public WindupResourceDoneable(WindupResource resource, Function<WindupResource, WindupResource> function) {
        super(resource, function);
    }
}

