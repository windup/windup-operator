package org.jboss.windup.operator;

import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.bundle.runtime.SharedCSVMetadata;

@CSVMetadata(
        annotations = @CSVMetadata.Annotations(
                containerImage = "quay.io/windupeng/windup-operator:${RELEASE_VERSION}",
                repository = "https://github.com/windup/windup-operator",
                categories = "Modernization & Migration",
                capabilities = "Basic Install",
                almExamples = """
                        [{
                          "kind": "Windup",
                          "apiVersion": "windup.jboss.org/v1",
                          "metadata": {
                            "name": "windup"
                          },
                          "spec": {}
                        }]
                        """
        ),
        permissionRules = {
                @CSVMetadata.PermissionRule(
                        apiGroups = {""},
                        resources = {"pods", "services", "services/finalizers", "endpoints", "persistentvolumeclaims", "events", "configmaps", "secrets"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"route.openshift.io"},
                        resources = {"routes"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"networking.k8s.io"},
                        resources = {"ingresses", "networkpolicies"},
                        verbs = {"*"}
                ),
                @CSVMetadata.PermissionRule(
                        apiGroups = {"apps"},
                        resources = {"deployments"},
                        verbs = {"*"}
                )
        },
        displayName = "Windup Operator",
        description = """
                Windup is a web console application that supports large-scale Java application modernization and migration projects across a broad range of transformations and use cases.
                                                                                                              
                It analyzes application code, supports effort estimation, accelerates code migration, and enables users to move applications to containers.
                                                                                                              
                For more information please refer to the https://windup.github.io/ page.
                               
                 """,
        installModes = {
                @CSVMetadata.InstallMode(type = "OwnNamespace", supported = true),
                @CSVMetadata.InstallMode(type = "SingleNamespace", supported = false),
                @CSVMetadata.InstallMode(type = "MultiNamespace", supported = false),
                @CSVMetadata.InstallMode(type = "AllNamespaces", supported = false)
        },
        keywords = {"windup", "migration", "modernization"},
        maturity = "alpha",
        provider = @CSVMetadata.Provider(name = "Windup"),
        links = {
                @CSVMetadata.Link(name = "Website", url = "windup.github.io/"),
                @CSVMetadata.Link(name = "Github", url = "https://github.com/windup/windup-operator")
        },
        icon = @CSVMetadata.Icon(fileName = "icon.png", mediatype = "image/png"),
        maintainers = {@CSVMetadata.Maintainer(name = "Windup", email = "migrate@redhat.com")}
)
public class WindupCSVMetadata implements SharedCSVMetadata {
}
