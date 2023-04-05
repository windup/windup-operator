package org.jboss.windup.operator.cdrs.v2alpha1;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.utils.CRDUtils;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Random;

@ApplicationScoped
public class DBSecret extends CRUDKubernetesDependentResource<Secret, Windup> implements Creator<Secret, Windup> {

    public DBSecret() {
        super(Secret.class);
    }

    @Override
    protected Secret desired(Windup cr, Context<Windup> context) {
        return newSecret(cr, context);
    }

    @Override
    public Matcher.Result<Secret> match(Secret actual, Windup cr, Context<Windup> context) {
        final var desiredSecretName = getSecretName(cr);
        return Matcher.Result.nonComputed(actual.getMetadata().getName().equals(desiredSecretName));
    }

    @SuppressWarnings("unchecked")
    private Secret newSecret(Windup cr, Context<Windup> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new SecretBuilder()
                .withNewMetadata()
                .withName(getSecretName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .withOwnerReferences(CRDUtils.getOwnerReference(cr))
                .endMetadata()
                .addToStringData(Constants.DB_SECRET_USERNAME, generateRandomString(10))
                .addToStringData(Constants.DB_SECRET_PASSWORD, generateRandomString(10))
                .addToStringData(Constants.DB_SECRET_DATABASE_NAME, "windup")
                .build();
    }

    public static String getSecretName(Windup cr) {
        return cr.getMetadata().getName() + Constants.DB_SECRET_SUFFIX;
    }

    public static String generateRandomString(int targetStringLength) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'

        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
