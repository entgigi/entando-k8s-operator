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

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.StartupEvent;
import java.util.Optional;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.IngressingDeployCommand;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.KeycloakConnectionSecret;
import org.entando.kubernetes.controller.common.KeycloakName;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServerSpec;

public class EntandoKeycloakServerController extends AbstractDbAwareController<EntandoKeycloakServer> {

    @Inject
    public EntandoKeycloakServerController(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    public EntandoKeycloakServerController(KubernetesClient kubernetesClient, boolean exitAutomatically) {
        super(kubernetesClient, exitAutomatically);
    }

    public EntandoKeycloakServerController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        super(k8sClient, keycloakClient);
    }

    public void onStartup(@Observes StartupEvent event) {
        processCommand();
    }

    @Override
    protected void synchronizeDeploymentState(EntandoKeycloakServer newEntandoKeycloakServer) {
        DatabaseServiceResult databaseServiceResult = prepareKeycloakDatabaseService(newEntandoKeycloakServer);
        Secret existingKeycloakAdminSecret = prepareKeycloakAdminSecretInControllerNamespace(newEntandoKeycloakServer);
        KeycloakServiceDeploymentResult serviceDeploymentResult = deployKeycloak(
                newEntandoKeycloakServer,
                databaseServiceResult,
                existingKeycloakAdminSecret);
        ensureHttpAccess(serviceDeploymentResult);
        ConfigMap keycloakConnectionConfigMap = saveConnectionInfoConfigMapInDeploymentNamespace(newEntandoKeycloakServer,
                serviceDeploymentResult);
        if (newEntandoKeycloakServer.getSpec().isDefault()) {
            k8sClient.entandoResources().loadDefaultConfigMap()
                    .addToData(KeycloakName.DEFAULT_KEYCLOAK_NAME_KEY, newEntandoKeycloakServer.getMetadata().getName())
                    .addToData(KeycloakName.DEFAULT_KEYCLOAK_NAMESPACE_KEY, newEntandoKeycloakServer.getMetadata().getNamespace())
                    .done();

        }
        ensureKeycloakRealm(new KeycloakConnectionSecret(existingKeycloakAdminSecret, keycloakConnectionConfigMap));
        k8sClient.entandoResources().updateStatus(newEntandoKeycloakServer, serviceDeploymentResult.getStatus());
    }

    private ConfigMap saveConnectionInfoConfigMapInDeploymentNamespace(EntandoKeycloakServer newEntandoKeycloakServer,
            KeycloakServiceDeploymentResult serviceDeploymentResult) {
        ConfigMap result = k8sClient.secrets().loadConfigMap(newEntandoKeycloakServer,
                KeycloakName.forTheConnectionConfigMap(newEntandoKeycloakServer));
        if (result == null) {
            //Don't overwrite if it is already there. This allows developers to override the URL_KEY where required
            ConfigMap newConfigMap = new ConfigMapBuilder().withNewMetadata()
                    .withNamespace(newEntandoKeycloakServer.getMetadata().getNamespace())
                    .withName(KeycloakName.forTheConnectionConfigMap(newEntandoKeycloakServer))
                    .endMetadata()
                    .addToData(KubeUtils.URL_KEY, serviceDeploymentResult.getExternalBaseUrl())
                    .addToData(KubeUtils.INTERNAL_URL_KEY, serviceDeploymentResult.getInternalBaseUrl()).build();
            k8sClient.secrets().createConfigMapIfAbsent(newEntandoKeycloakServer, newConfigMap);
            return newConfigMap;
        } else {
            return result;
        }

    }

    private void ensureHttpAccess(KeycloakServiceDeploymentResult serviceDeploymentResult) {
        //Give the operator access over http for cluster.local calls
        k8sClient.pods().executeOnPod(serviceDeploymentResult.getPod(), "server-container", 30,
                "cd ${KEYCLOAK_HOME}/bin",
                "./kcadm.sh config credentials --server http://localhost:8080/auth --realm master "
                        + "--user  ${KEYCLOAK_USER:-${SSO_ADMIN_USERNAME}} "
                        + "--password ${KEYCLOAK_PASSWORD:-${SSO_ADMIN_PASSWORD}}",
                "./kcadm.sh update realms/master -s sslRequired=NONE"
        );
    }

    private KeycloakServiceDeploymentResult deployKeycloak(EntandoKeycloakServer newEntandoKeycloakServer,
            DatabaseServiceResult databaseServiceResult, Secret existingKeycloakAdminSecret) {
        // Create the Keycloak service using the provided database and  the locally stored keycloak admin credentials
        // for this EntandoKeycloakServer.
        KeycloakDeployable keycloakDeployable = new KeycloakDeployable(
                newEntandoKeycloakServer,
                databaseServiceResult,
                existingKeycloakAdminSecret);
        IngressingDeployCommand<KeycloakServiceDeploymentResult, EntandoKeycloakServerSpec> keycloakCommand = new IngressingDeployCommand<>(
                keycloakDeployable);
        return keycloakCommand.execute(k8sClient, Optional.of(keycloakClient)).withStatus(keycloakCommand.getStatus());
    }

    private Secret prepareKeycloakAdminSecretInControllerNamespace(EntandoKeycloakServer newEntandoKeycloakServer) {
        Secret existingKeycloakAdminSecret = k8sClient.secrets()
                .loadControllerSecret(KeycloakName.forTheAdminSecret(newEntandoKeycloakServer));
        if (existingKeycloakAdminSecret == null) {
            //We need to FIRST populate the secret in the controller's namespace so that, if deployment fails, we have the credentials
            // that the secret in the Deployment's namespace was based on, because we may not have read access to it.
            existingKeycloakAdminSecret = new SecretBuilder()
                    .withNewMetadata()
                    .withName(KeycloakName.forTheAdminSecret(newEntandoKeycloakServer))
                    .endMetadata()
                    .addToStringData(KubeUtils.USERNAME_KEY, "entando_keycloak_admin")
                    .addToStringData(KubeUtils.PASSSWORD_KEY, KubeUtils.randomAlphanumeric(12))
                    .build();
            k8sClient.secrets().overwriteControllerSecret(existingKeycloakAdminSecret);

        }
        return existingKeycloakAdminSecret;
    }

    private DatabaseServiceResult prepareKeycloakDatabaseService(EntandoKeycloakServer newEntandoKeycloakServer) {
        // Create database for Keycloak
        return prepareDatabaseService(newEntandoKeycloakServer, EntandoKeycloakHelper.determineDbmsVendor(newEntandoKeycloakServer));
    }

    private void ensureKeycloakRealm(KeycloakConnectionConfig keycloakConnectionConfig) {
        logger.severe(() -> format("Attempting to log into Keycloak at %s", keycloakConnectionConfig.determineBaseUrl()));
        keycloakClient.login(keycloakConnectionConfig.determineBaseUrl(), keycloakConnectionConfig.getUsername(),
                keycloakConnectionConfig.getPassword());
        keycloakClient.ensureRealm(KubeUtils.ENTANDO_DEFAULT_KEYCLOAK_REALM);
    }

}
