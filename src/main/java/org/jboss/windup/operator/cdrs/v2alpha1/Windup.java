package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import org.jboss.windup.operator.Constants;

@Group(Constants.CRDS_GROUP)
@Version(Constants.CRDS_VERSION)
public class Windup extends CustomResource<WindupSpec, WindupStatus> implements Namespaced {

    @Override
    protected WindupSpec initSpec() {
        return new WindupSpec();
    }

    @Override
    protected WindupStatus initStatus() {
        return new WindupStatus();
    }

}

