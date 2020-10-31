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

package org.entando.kubernetes.controller.spi;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.KeycloakName;
import org.entando.kubernetes.model.app.KeycloakAwareSpec;

public interface KeycloakAware extends DeployableContainer, HasWebContext {

    KeycloakConnectionConfig getKeycloakConnectionConfig();

    KeycloakClientConfig getKeycloakClientConfig();

    KeycloakAwareSpec getKeycloakAwareSpec();

    default String determineRealm() {
        KeycloakAwareSpec keycloakAwareSpec = getKeycloakAwareSpec();
        return KeycloakName.ofTheRealm(keycloakAwareSpec);

    }

    default void addKeycloakVariables(List<EnvVar> vars) {
        KeycloakConnectionConfig keycloakDeployment = getKeycloakConnectionConfig();
        vars.add(new EnvVar("KEYCLOAK_ENABLED", "true", null));
        vars.add(new EnvVar("KEYCLOAK_REALM", determineRealm(), null));
        vars.add(new EnvVar("KEYCLOAK_PUBLIC_CLIENT_ID", determinePublicClient(), null));
        vars.add(new EnvVar("KEYCLOAK_AUTH_URL", keycloakDeployment.getExternalBaseUrl(), null));
        String keycloakSecretName = KeycloakName.forTheClientSecret(getKeycloakClientConfig());
        vars.add(new EnvVar("KEYCLOAK_CLIENT_SECRET", null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_SECRET_KEY)));
        vars.add(new EnvVar("KEYCLOAK_CLIENT_ID", null,
                KubeUtils.secretKeyRef(keycloakSecretName, KeycloakName.CLIENT_ID_KEY)));

    }

    default String determinePublicClient() {
        KeycloakAwareSpec keycloakAwareSpec = getKeycloakAwareSpec();
        return KeycloakName.ofThePublicClient(keycloakAwareSpec);
    }
}
