/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.keycloakserver;

import static io.qameta.allure.Allure.step;
import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfigProperty;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.capability.CapabilityRequirement;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.ProvidedCapability;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.common.EntandoDeploymentPhase;
import org.entando.kubernetes.model.common.ResourceReference;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.test.common.SourceLink;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("component"), @Tag("in-process"), @Tag("inner-hexagon")})
@Feature("As a controller developer, I would like request a capability that will allow me to use an external Keycloak service so that I "
        + "can leverage an existing user database")
@SourceLink("ExternalKeycloakCapabilityTest.java")
class ExternalKeycloakCapabilityTest extends KeycloakTestBase {

    public static final String SPECIFIED_SSO = "specified-sso";

    @Test
    @Description("Should link to external SSO service when all required fields are provided")
    void shouldLinkToExternalService() {
        step("Given I have configured the operator run in Red Hat Compliance mode", () -> {
            attachEnvironmentVariable(EntandoOperatorSpiConfigProperty.ENTANDO_K8S_OPERATOR_COMPLIANCE_MODE, "redhat");
        });
        step("And I have configured a secret with admin credentials to a remote Keycloak server", () -> {
            final Secret adminSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(MY_NAMESPACE)
                    .withName("my-existing-sso-admin-secret")
                    .endMetadata()
                    .addToData(SecretUtils.USERNAME_KEY, "someuser")
                    .addToData(SecretUtils.PASSSWORD_KEY, "somepassword")
                    .build();
            getClient().secrets().createSecretIfAbsent(newResourceRequiringCapability(), adminSecret);
            attachKubernetesResource("Existing Admin Secret", adminSecret);
        });
        step("When I request an SSO Capability  with its name and namespace explicitly specified, provisioned externally",
                () -> runControllerAgainstCapabilityRequirement(newResourceRequiringCapability(), new CapabilityRequirementBuilder()
                        .withCapability(StandardCapability.SSO)
                        .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                        .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                        .withNewExternallyProvidedService()
                        .withPath("/auth")
                        .withHost("kc.apps.serv.run")
                        .withPort(8080)
                        .withAdminSecretName("my-existing-sso-admin-secret")
                        .endExternallyProvidedService()
                        .withSpecifiedCapability(new ResourceReference(MY_NAMESPACE, SPECIFIED_SSO))
                        .addAllToCapabilityParameters(Map.of(ProvidedSsoCapability.DEFAULT_REALM_PARAMETER, "my-realm"))
                        .build()));
        final ProvidedCapability providedCapability = client.entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, SPECIFIED_SSO);
        final EntandoKeycloakServer entandoKeycloakServer = client.entandoResources()
                .load(EntandoKeycloakServer.class, MY_NAMESPACE, SPECIFIED_SSO);
        step("Then an EntandoKeycloakServer was provisioned:", () -> {
            step("with the name explicitly specified", () -> {
                assertThat(entandoKeycloakServer.getMetadata().getName()).isEqualTo(SPECIFIED_SSO);
                assertThat(providedCapability.getMetadata().getName()).isEqualTo(SPECIFIED_SSO);
                assertThat(providedCapability.getSpec().getSpecifiedCapability().get().getName()).isEqualTo(SPECIFIED_SSO);
            });

            step("using the 'Use External' provisioningStrategy",
                    () -> assertThat(entandoKeycloakServer.getSpec().getProvisioningStrategy()).contains(
                            CapabilityProvisioningStrategy.USE_EXTERNAL));
            step("and it is owned by the ProvidedCapability to ensure only changes from the ProvidedCapability will change the "
                            + "implementing Kubernetes resources",
                    () -> assertThat(ResourceUtils.customResourceOwns(providedCapability, entandoKeycloakServer)));
            step("and its frontEndUrl property reflects the connection info provided in the CapabilityRequirement",
                    () -> assertThat(entandoKeycloakServer.getSpec().getFrontEndUrl()).contains("https://kc.apps.serv.run:8080/auth"));
            step("and the ProvidedCapability's status carries the name of the correct admin secret to use",
                    () -> assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getAdminSecretName())
                            .contains("my-existing-sso-admin-secret"));
            step("and the ProvidedCapability's status carries the base url where the SSO service can be accessed",
                    () -> assertThat(
                            providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getExternalBaseUrl())
                            .contains("https://kc.apps.serv.run:8080/auth"));
            attachKubernetesResource("EntandoKeycloakServer", entandoKeycloakServer);
        });
        final ProvidedSsoCapability providedKeycloak = new ProvidedSsoCapability(
                client.capabilities().buildCapabilityProvisioningResult(providedCapability));
        step("And the provided Keycloak connection info reflects the external service", () -> {

            step("using the 'Use External' provisioningStrategy",
                    () -> assertThat(providedKeycloak.getBaseUrlToUse()).isEqualTo("https://kc.apps.serv.run:8080/auth"));
            step("and the default realm 'my-realm'",
                    () -> assertThat(providedKeycloak.getDefaultRealm()).contains("my-realm"));
        });
        step("And no DBMS Capability was requested", () -> {
            final Collection<ProvidedCapability> providedCapabilities = getClient().getNamespaces()
                    .get(entandoKeycloakServer.getMetadata().getNamespace())
                    .getCustomResources(ProvidedCapability.class.getSimpleName())
                    .values()
                    .stream()
                    .map(ProvidedCapability.class::cast)
                    .collect(Collectors.toList());
            assertThat(providedCapabilities).noneMatch(resource -> resource.getSpec().getCapability() == StandardCapability.DBMS);
        });
    }

    @Test
    @Description("Should omit the standard ports in the frontEndUrl when linking an external SSO service")
    void shouldLinkToExternalServiceOnPort443() {
        step("Given I have configured a secret with admin credentials to a remote Keycloak server", () -> {
            final Secret adminSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(MY_NAMESPACE)
                    .withName("my-existing-sso-admin-secret")
                    .endMetadata()
                    .addToData(SecretUtils.USERNAME_KEY, "someuser")
                    .addToData(SecretUtils.PASSSWORD_KEY, "somepassword")
                    .build();
            getClient().secrets().createSecretIfAbsent(newResourceRequiringCapability(), adminSecret);
            attachKubernetesResource("Existing Admin Secret", adminSecret);
        });
        step("When I request an SSO Capability  with its name and namespace explicitly specified, provisioned externally",
                () -> runControllerAgainstCapabilityRequirement(newResourceRequiringCapability(), new CapabilityRequirementBuilder()
                        .withCapability(StandardCapability.SSO)
                        .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                        .withResolutionScopePreference(CapabilityScope.SPECIFIED)
                        .withNewExternallyProvidedService()
                        .withPath("/auth")
                        .withHost("kc.apps.serv.run")
                        .withPort(443)
                        .withAdminSecretName("my-existing-sso-admin-secret")
                        .endExternallyProvidedService()
                        .withSpecifiedCapability(new ResourceReference(MY_NAMESPACE, SPECIFIED_SSO))
                        .addAllToCapabilityParameters(Map.of(ProvidedSsoCapability.DEFAULT_REALM_PARAMETER, "my-realm"))
                        .build()));
        final ProvidedCapability providedCapability = client.entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, SPECIFIED_SSO);
        final EntandoKeycloakServer entandoKeycloakServer = client.entandoResources()
                .load(EntandoKeycloakServer.class, MY_NAMESPACE, SPECIFIED_SSO);
        step("Then an EntandoKeycloakServer was provisioned:", () -> {
            step("with the name explicitly specified", () -> {
                assertThat(entandoKeycloakServer.getMetadata().getName()).isEqualTo(SPECIFIED_SSO);
                assertThat(providedCapability.getMetadata().getName()).isEqualTo(SPECIFIED_SSO);
                assertThat(providedCapability.getSpec().getSpecifiedCapability().get().getName()).isEqualTo(SPECIFIED_SSO);
            });

            step("using the 'Use External' provisioningStrategy",
                    () -> assertThat(entandoKeycloakServer.getSpec().getProvisioningStrategy()).contains(
                            CapabilityProvisioningStrategy.USE_EXTERNAL));
            step("and it is owned by the ProvidedCapability to ensure only changes from the ProvidedCapability will change the "
                            + "implementing Kubernetes resources",
                    () -> assertThat(ResourceUtils.customResourceOwns(providedCapability, entandoKeycloakServer)));
            step("and its frontEndUrl property reflects the connection info provided in the CapabilityRequirement",
                    () -> assertThat(entandoKeycloakServer.getSpec().getFrontEndUrl()).contains("https://kc.apps.serv.run/auth"));
            step("and the ProvidedCapability's status carries the name of the correct admin secret to use",
                    () -> assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getAdminSecretName())
                            .contains("my-existing-sso-admin-secret"));
            step("and the ProvidedCapability's status carries the base url where the SSO service can be accessed",
                    () -> assertThat(
                            providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get().getExternalBaseUrl())
                            .contains("https://kc.apps.serv.run/auth"));
            attachKubernetesResource("EntandoKeycloakServer", entandoKeycloakServer);
        });
        final ProvidedSsoCapability providedKeycloak = new ProvidedSsoCapability(
                client.capabilities().buildCapabilityProvisioningResult(providedCapability));
        step("And the provided Keycloak connection info reflects the external service", () -> {

            step("using the 'Use External' provisioningStrategy",
                    () -> assertThat(providedKeycloak.getBaseUrlToUse()).isEqualTo("https://kc.apps.serv.run/auth"));
            step("and the default realm 'my-realm'",
                    () -> assertThat(providedKeycloak.getDefaultRealm()).contains("my-realm"));
        });
    }

    @Test
    @Description("Should fail when the admin secret specified is absent in the deployment namespace")
    void shouldFailWhenAdminSecretAbsent() {
        step("Given I have configured not configured a secret with admin credentials to a remote Keycloak server");
        step("When I request an SSO Capability that is externally provided to a non-existing admin secret", () -> {
            final SerializedEntandoResource forResource = newResourceRequiringCapability();
            final CapabilityRequirement build = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.SSO)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                    .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                    .withNewExternallyProvidedService()
                    .withPath("/auth")
                    .withHost("kc.apps.serv.run")
                    .withPort(8080)
                    .withAdminSecretName("my-existing-sso-admin-secret")
                    .endExternallyProvidedService()
                    .build();
            runControllerAgainstCapabilityRequirement(forResource, build);
        });
        final ProvidedCapability providedCapability = client.entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, DEFAULT_SSO_IN_NAMESPACE);
        final EntandoKeycloakServer entandoKeycloakServer = client.entandoResources()
                .load(EntandoKeycloakServer.class, MY_NAMESPACE, DEFAULT_SSO_IN_NAMESPACE);
        step("And the resulting status objects of both the ProvidedCapability and EntandoKeycloakServer reflect the failure and the cause"
                        + " for the failure",
                () -> {
                    attachKubernetesResource("EntandoKeycloakServer.status", entandoKeycloakServer.getStatus());
                    attachKubernetesResource("ProvidedCapability.status", providedCapability.getStatus());
                    step("The phase of the statuses of both the ProvidedCapability and EntandoKeycloakServer is FAILED", () -> {
                        assertThat(entandoKeycloakServer.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
                        assertThat(providedCapability.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
                    });
                    step("And the statuses of  both the ProvidedCapability and EntandoKeycloakServer reflect the correct error message",
                            () -> {
                                assertThat(entandoKeycloakServer.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()
                                        .getEntandoControllerFailure()
                                        .get().getDetailMessage()).contains(
                                        "Please ensure that a secret with the name 'my-existing-sso-admin-secret' exists in the requested"
                                                + " namespace");
                                assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()
                                        .getEntandoControllerFailure()
                                        .get().getDetailMessage()).contains(
                                        "Please ensure that a secret with the name 'my-existing-sso-admin-secret' exists in the requested"
                                                + " namespace");
                            });
                });
    }

    @Test
    @Description("Should fail when no host name is specified")
    void shouldFailWhenNoHostNameSpecified() {
        final SerializedEntandoResource forResource = newResourceRequiringCapability();
        step("Given I have configured a secret with admin credentials to a remote Keycloak server", () -> {
            final Secret adminSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(MY_NAMESPACE)
                    .withName("my-existing-sso-admin-secret")
                    .endMetadata()
                    .addToData(SecretUtils.USERNAME_KEY, "someuser")
                    .addToData(SecretUtils.PASSSWORD_KEY, "somepassword")
                    .build();
            getClient().secrets().createSecretIfAbsent(forResource, adminSecret);
            attachKubernetesResource("Existing Admin Secret", adminSecret);
        });
        step("When I request an SSO Capability that is externally provided to a non-existing admin secret", () -> {
            final CapabilityRequirement capabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.SSO)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                    .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                    .withNewExternallyProvidedService()
                    .withPath("/auth")
                    .withHost(null)//NO HOST!!!
                    .withPort(8080)
                    .withAdminSecretName("my-existing-sso-admin-secret")
                    .endExternallyProvidedService()
                    .build();
            runControllerAgainstCapabilityRequirement(forResource, capabilityRequirement);
        });
        final ProvidedCapability providedCapability = client.entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, DEFAULT_SSO_IN_NAMESPACE);
        final EntandoKeycloakServer entandoKeycloakServer = client.entandoResources()
                .load(EntandoKeycloakServer.class, MY_NAMESPACE, DEFAULT_SSO_IN_NAMESPACE);
        step("Then the resulting status objects of both the ProvidedCapability and EntandoKeycloakServer reflect the failure and the cause"
                        + " for the failure",
                () -> {
                    attachKubernetesResource("EntandoKeycloakServer.status", entandoKeycloakServer.getStatus());
                    attachKubernetesResource("ProvidedCapability.status", providedCapability.getStatus());
                    step("The phase of the statuses of both the ProvidedCapability and EntandoKeycloakServer is FAILED", () -> {
                        assertThat(entandoKeycloakServer.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
                        assertThat(providedCapability.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
                    });
                    step("And the statuses of  both the ProvidedCapability and EntandoKeycloakServer reflect the correct error message",
                            () -> {
                                assertThat(entandoKeycloakServer.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()
                                        .getEntandoControllerFailure()
                                        .get().getDetailMessage()).contains(
                                        "Please provide the hostname of the SSO service you intend to connect to");
                                assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()
                                        .getEntandoControllerFailure()
                                        .get().getDetailMessage()).contains(
                                        "Please provide the hostname of the SSO service you intend to connect to");
                            });
                });
    }

    @Test
    @Description("Should fail when no admin secret name is specified")
    void shouldFailWhenNoAdminSecretName() {
        final SerializedEntandoResource forResource = newResourceRequiringCapability();
        step("Given I have configured a secret with admin credentials to a remote Keycloak server", () -> {
            final Secret adminSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withNamespace(MY_NAMESPACE)
                    .withName("my-existing-sso-admin-secret")
                    .endMetadata()
                    .addToData(SecretUtils.USERNAME_KEY, "someuser")
                    .addToData(SecretUtils.PASSSWORD_KEY, "somepassword")
                    .build();
            getClient().secrets().createSecretIfAbsent(forResource, adminSecret);
            attachKubernetesResource("Existing Admin Secret", adminSecret);
        });
        step("When I request an SSO Capability that is externally provided to a non-existing admin secret", () -> {
            final CapabilityRequirement capabilityRequirement = new CapabilityRequirementBuilder()
                    .withCapability(StandardCapability.SSO)
                    .withProvisioningStrategy(CapabilityProvisioningStrategy.USE_EXTERNAL)
                    .withResolutionScopePreference(CapabilityScope.NAMESPACE)
                    .withNewExternallyProvidedService()
                    .withPath("/auth")
                    .withHost("myhost.com")
                    .withPort(8080)
                    .withAdminSecretName(null)//NO ADMIN SECRET!!
                    .endExternallyProvidedService()
                    .build();
            runControllerAgainstCapabilityRequirement(forResource, capabilityRequirement);
        });
        final ProvidedCapability providedCapability = client.entandoResources()
                .load(ProvidedCapability.class, MY_NAMESPACE, DEFAULT_SSO_IN_NAMESPACE);
        final EntandoKeycloakServer entandoKeycloakServer = client.entandoResources()
                .load(EntandoKeycloakServer.class, MY_NAMESPACE, DEFAULT_SSO_IN_NAMESPACE);
        step("Then the resulting status objects of both the ProvidedCapability and EntandoKeycloakServer reflect the failure and the cause"
                        + " for the failure",
                () -> {
                    attachKubernetesResource("EntandoKeycloakServer.status", entandoKeycloakServer.getStatus());
                    attachKubernetesResource("ProvidedCapability.status", providedCapability.getStatus());
                    step("The phase of the statuses of both the ProvidedCapability and EntandoKeycloakServer is FAILED", () -> {
                        assertThat(entandoKeycloakServer.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
                        assertThat(providedCapability.getStatus().getPhase()).isEqualTo(EntandoDeploymentPhase.FAILED);
                    });
                    step("And the statuses of  both the ProvidedCapability and EntandoKeycloakServer reflect the correct error message",
                            () -> {
                                assertThat(entandoKeycloakServer.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()
                                        .getEntandoControllerFailure()
                                        .get().getDetailMessage()).contains(
                                        "Please provide the name of the secret containing the admin credentials for the SSO service you "
                                                + "intend to connect to");
                                assertThat(providedCapability.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).get()
                                        .getEntandoControllerFailure()
                                        .get().getDetailMessage()).contains(
                                        "Please provide the name of the secret containing the admin credentials for the SSO service you "
                                                + "intend to connect to");
                            });
                });
    }
}
