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

package org.entando.kubernetes.controller.app;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.InfrastructureConfig;
import org.entando.kubernetes.controller.database.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.database.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.DbAware;
import org.entando.kubernetes.controller.spi.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.SecretToMount;
import org.entando.kubernetes.controller.spi.SpringBootDeployableContainer;
import org.entando.kubernetes.model.EntandoIngressingDeploymentSpec;
import org.entando.kubernetes.model.KeycloakAwareSpec;
import org.entando.kubernetes.model.app.EntandoApp;
import org.entando.kubernetes.model.plugin.Permission;

public class ComponentManagerDeployableContainer implements SpringBootDeployableContainer, PersistentVolumeAware, ParameterizableContainer {

    public static final String COMPONENT_MANAGER_QUALIFIER = "de";
    public static final String COMPONENT_MANAGER_IMAGE_NAME = "entando/entando-component-manager";

    private static final String DEDB = "dedb";
    public static final String ECR_GIT_CONFIG_DIR = "/etc/ecr-git-config";
    private final EntandoApp entandoApp;
    private final KeycloakConnectionConfig keycloakConnectionConfig;
    private final Optional<InfrastructureConfig> infrastructureConfig;
    private final EntandoAppDeploymentResult entandoAppDeployment;
    private final List<DatabaseSchemaConnectionInfo> databaseSchemaConnectionInfo;

    private static final DbmsVendorConfig DEFAULT_EMBEDDED_VENDOR = DbmsVendorConfig.H2;

    public ComponentManagerDeployableContainer(
            EntandoApp entandoApp,
            KeycloakConnectionConfig keycloakConnectionConfig,
            InfrastructureConfig infrastructureConfig,
            EntandoAppDeploymentResult entandoAppDeployment,
            DatabaseServiceResult databaseServiceResult) {
        this.entandoApp = entandoApp;
        this.keycloakConnectionConfig = keycloakConnectionConfig;
        this.infrastructureConfig = Optional.ofNullable(infrastructureConfig);
        this.entandoAppDeployment = entandoAppDeployment;

        this.databaseSchemaConnectionInfo = Optional.ofNullable(databaseServiceResult)
                .map(dsr -> DbAware.buildDatabaseSchemaConnectionInfo(entandoApp, dsr, Collections.singletonList(DEDB)))
                .orElse(Collections.emptyList());

    }

    @Override
    public String determineImageToUse() {
        return COMPONENT_MANAGER_IMAGE_NAME;
    }

    @Override
    public String getNameQualifier() {
        return COMPONENT_MANAGER_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return 8083;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        String entandoUrl = entandoAppDeployment.getInternalBaseUrl();
        vars.add(new EnvVar("ENTANDO_APP_NAME", entandoApp.getMetadata().getName(), null));
        vars.add(new EnvVar("ENTANDO_URL", entandoUrl, null));
        vars.add(new EnvVar("SERVER_PORT", String.valueOf(getPrimaryPort()), null));
        infrastructureConfig.ifPresent(c -> vars.add(new EnvVar("ENTANDO_K8S_SERVICE_URL", c.getK8SExternalServiceUrl(), null)));
        //The ssh files will be copied to /opt/.ssh and chmod to 400. This can only happen at runtime because Openshift generates a
        // random userid
        entandoApp.getSpec().getEcrGitSshSecretName().ifPresent(s -> vars.add(new EnvVar("GIT_SSH_COMMAND", "ssh "
                + "-o UserKnownHostsFile=/opt/.ssh/known_hosts "
                + "-i /opt/.ssh/id_rsa "
                + "-o IdentitiesOnly=yes", null)));
        return vars;
    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = SpringBootDeployableContainer.super.getDatabaseConnectionVariables();
        if (!getDatabaseSchema().isPresent()) {
            vars.add(new EnvVar(SpringProperty.SPRING_JPA_DATABASE_PLATFORM.name(), DEFAULT_EMBEDDED_VENDOR.getHibernateDialect(), null));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_USERNAME.name(), DEFAULT_EMBEDDED_VENDOR.getDefaultAdminUsername(), null));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_PASSWORD.name(), DEFAULT_EMBEDDED_VENDOR.getDefaultAdminPassword(), null));
            vars.add(new EnvVar(SpringProperty.SPRING_DATASOURCE_URL.name(), DEFAULT_EMBEDDED_VENDOR.getConnectionStringBuilder()
                    .inFolder("/entando-data/databases/" + COMPONENT_MANAGER_QUALIFIER)
                    .usingDatabase(DEFAULT_EMBEDDED_VENDOR.toString().toLowerCase() + ".db")
                    .buildConnectionString(), null));
        }
        return vars;

    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return this.databaseSchemaConnectionInfo;
    }

    @Override
    public List<SecretToMount> getSecretsToMount() {
        List<SecretToMount> result = new ArrayList<>();
        entandoApp.getSpec().getEcrGitSshSecretName().ifPresent(s -> result.add(new SecretToMount(s, ECR_GIT_CONFIG_DIR)));
        return result;
    }

    @Override
    public int getMemoryLimitMebibytes() {
        return 768;
    }

    @Override
    public Optional<DatabaseSchemaConnectionInfo> getDatabaseSchema() {
        return databaseSchemaConnectionInfo.stream().findFirst();
    }

    @Override
    public int getCpuLimitMillicores() {
        return 750;
    }

    public KeycloakConnectionConfig getKeycloakConnectionConfig() {
        return keycloakConnectionConfig;
    }

    @Override
    public KeycloakClientConfig getKeycloakClientConfig() {
        String entandoAppClientId = EntandoAppDeployableContainer.clientIdOf(entandoApp);
        String clientId = entandoApp.getMetadata().getName() + "-" + getNameQualifier();
        List<Permission> permissions = new ArrayList<>();
        permissions.add(new Permission(entandoAppClientId, "superuser"));
        this.infrastructureConfig.ifPresent(c -> permissions.add(new Permission(c.getK8sServiceClientId(), KubeUtils.ENTANDO_APP_ROLE)));
        return new KeycloakClientConfig(determineRealm(), clientId, clientId,
                Collections.emptyList(),
                permissions);
    }

    @Override
    public KeycloakAwareSpec getKeycloakAwareSpec() {
        return entandoApp.getSpec();
    }

    @Override
    public String getWebContextPath() {
        return "/digital-exchange";
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath() + "/actuator/health");
    }

    @Override
    public String getVolumeMountPath() {
        return "/entando-data";
    }

    @Override
    public EntandoIngressingDeploymentSpec getCustomResourceSpec() {
        return getKeycloakAwareSpec();
    }
}
