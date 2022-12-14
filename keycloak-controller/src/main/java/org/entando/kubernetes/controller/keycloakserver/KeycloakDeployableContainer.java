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

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Secret;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.spi.common.DbmsVendorConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.common.SecretUtils;
import org.entando.kubernetes.controller.spi.common.TrustStoreHelper;
import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.container.DatabaseSchemaConnectionInfo;
import org.entando.kubernetes.controller.spi.container.DbAwareContainer;
import org.entando.kubernetes.controller.spi.container.DockerImageInfo;
import org.entando.kubernetes.controller.spi.container.IngressingContainer;
import org.entando.kubernetes.controller.spi.container.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;
import org.entando.kubernetes.controller.spi.container.SecretToMount;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.controller.support.client.SecretClient;
import org.entando.kubernetes.model.capability.CapabilityProvisioningStrategy;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoResourceRequirements;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.StandardKeycloakImage;

public class KeycloakDeployableContainer implements IngressingContainer, DbAwareContainer, PersistentVolumeAwareContainer,
        ParameterizableContainer, ConfigurableResourceContainer {

    private static final String COMMUNITY_KEYCLOAK_IMAGE_NAME = "entando/entando-keycloak";
    public static final String REDHAT_SSO_IMAGE_NAME = "entando/entando-redhat-sso";

    private final EntandoKeycloakServer keycloakServer;
    private final DatabaseConnectionInfo databaseServiceResult;
    private final Secret caCertSecret;
    private final List<DatabaseSchemaConnectionInfo> databaseSchemaConnectionInfos;

    public KeycloakDeployableContainer(EntandoKeycloakServer keycloakServer, DatabaseConnectionInfo databaseServiceResult,
            Secret caCertSecret, SecretClient secretClient) {
        this.keycloakServer = keycloakServer;
        this.databaseServiceResult = databaseServiceResult;
        this.caCertSecret = caCertSecret;
        databaseSchemaConnectionInfos = Optional.ofNullable(databaseServiceResult)
                .map(databaseServiceResult1 -> DbAwareContainer.buildDatabaseSchemaConnectionInfo(keycloakServer,
                        databaseServiceResult, Collections.singletonList("db"), secretClient))
                .orElse(Collections.emptyList());
    }

    public static String secretName(EntandoKeycloakServer keycloakServer) {
        return keycloakServer.getSpec().getAdminSecretName().orElse(NameUtils.standardAdminSecretName(keycloakServer));
    }

    @Override
    public Optional<String> getStorageClass() {
        return this.keycloakServer.getSpec().getStorageClass().or(PersistentVolumeAwareContainer.super::getStorageClass);
    }

    @Override
    public Optional<Integer> getMaximumStartupTimeSeconds() {
        return Optional.of(180);
    }

    @Override
    public DockerImageInfo getDockerImageInfo() {
        return new DockerImageInfo(keycloakServer.getSpec().getCustomImage()
                .orElse(determineStandardImageName()));
    }

    private String determineStandardImageName() {
        if (determineStandardKeycloakImage() == StandardKeycloakImage.REDHAT_SSO) {
            return REDHAT_SSO_IMAGE_NAME;
        } else {
            return COMMUNITY_KEYCLOAK_IMAGE_NAME;
        }
    }

    @Override
    public List<SecretToMount> getSecretsToMount() {
        List<SecretToMount> result = new ArrayList<>();
        Optional.ofNullable(caCertSecret).ifPresent(
                s -> result.add(new SecretToMount(s.getMetadata().getName(), caCertsFolder(s))));
        return result;
    }

    protected String caCertsFolder(Secret s) {
        return TrustStoreHelper.CERT_SECRET_MOUNT_ROOT + File.separator + s.getMetadata().getName();
    }

    private StandardKeycloakImage determineStandardKeycloakImage() {
        return EntandoKeycloakHelper.determineStandardImage(keycloakServer);
    }

    @Override
    public int getMemoryLimitMebibytes() {
        return 1200;
    }

    @Override
    public int getCpuLimitMillicores() {
        return 1000;
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DEFAULT_SERVER_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return 8080;
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        if (determineStandardKeycloakImage() == StandardKeycloakImage.REDHAT_SSO) {
            vars.add(
                    new EnvVar("SSO_ADMIN_USERNAME", null, SecretUtils.secretKeyRef(secretName(keycloakServer), SecretUtils.USERNAME_KEY)));
            vars.add(new EnvVar("SSO_ADMIN_PASSWORD", null,
                    SecretUtils.secretKeyRef(secretName(keycloakServer), SecretUtils.PASSSWORD_KEY)));
        } else {
            vars.add(new EnvVar("KEYCLOAK_USER", null, SecretUtils.secretKeyRef(secretName(keycloakServer), SecretUtils.USERNAME_KEY)));
            vars.add(
                    new EnvVar("KEYCLOAK_PASSWORD", null, SecretUtils.secretKeyRef(secretName(keycloakServer), SecretUtils.PASSSWORD_KEY)));
        }
        vars.add(new EnvVar("PROXY_ADDRESS_FORWARDING", "true", null));
        Optional.ofNullable(caCertSecret).ifPresent(s -> vars.add(getX509CaBundleVariable(s)));
        return vars;
    }

    @Override
    public List<EnvVar> getDatabaseConnectionVariables() {
        List<EnvVar> vars = new ArrayList<>();
        if (EntandoKeycloakHelper.provisioningStrategyOf(keycloakServer) == CapabilityProvisioningStrategy.DEPLOY_DIRECTLY) {
            DbmsVendor dbmsVendor = EntandoKeycloakHelper.determineDbmsVendor(keycloakServer);
            if (dbmsVendor == DbmsVendor.EMBEDDED) {
                vars.add(new EnvVar("DB_VENDOR", "h2", null));
            } else {
                DatabaseSchemaConnectionInfo databaseSchemaConnectionInfo = databaseSchemaConnectionInfos.get(0);
                if (determineStandardKeycloakImage() == StandardKeycloakImage.REDHAT_SSO) {
                    String driverName = databaseSchemaConnectionInfo.getDatabaseServiceResult().getVendor().getName();
                    vars.add(new EnvVar(format("DB_%s_SERVICE_HOST", driverName.toUpperCase(Locale.ROOT)),
                            databaseSchemaConnectionInfo.getDatabaseServiceResult().getInternalServiceHostname(), null));
                    vars.add(new EnvVar(format("DB_%s_SERVICE_PORT", driverName.toUpperCase(Locale.ROOT)),
                            databaseSchemaConnectionInfo.getDatabaseServiceResult().getPort(), null));
                    vars.add(new EnvVar("DB_SERVICE_PREFIX_MAPPING", format("db-%s=DB", driverName), null));
                    vars.add(new EnvVar("DB_USERNAME", null, databaseSchemaConnectionInfo.getUsernameRef()));
                } else {
                    vars.add(new EnvVar("DB_ADDR", databaseSchemaConnectionInfo.getDatabaseServiceResult().getInternalServiceHostname(),
                            null));
                    vars.add(new EnvVar("DB_PORT", databaseSchemaConnectionInfo.getDatabaseServiceResult().getPort(), null));
                    vars.add(new EnvVar("DB_USER", null, databaseSchemaConnectionInfo.getUsernameRef()));
                }

                vars.add(new EnvVar("DB_VENDOR", determineKeycloaksNonStandardDbVendorName(databaseSchemaConnectionInfo), null));

                if (dbmsVendor == DbmsVendor.MYSQL) {
                    vars.add(new EnvVar("DB_DATABASE", null, databaseSchemaConnectionInfo.getUsernameRef()));
                } else {
                    vars.add(new EnvVar("DB_DATABASE", databaseSchemaConnectionInfo.getDatabaseNameToUse(), null));
                }
                vars.add(new EnvVar("DB_PASSWORD", null, databaseSchemaConnectionInfo.getPasswordRef()));
                vars.add(new EnvVar("JDBC_PARAMS",
                        databaseServiceResult.getJdbcParameters().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(
                                        Collectors.joining("&")), null));

            }
        }
        return vars;
    }

    @Override
    public List<DatabaseSchemaConnectionInfo> getSchemaConnectionInfo() {
        return this.databaseSchemaConnectionInfos;
    }

    public EnvVar getX509CaBundleVariable(Secret caCertSecret) {

        String certFiles = caCertSecret.getData().keySet().stream()
                .map(fileName -> caCertsFolder(caCertSecret) + File.separator + fileName)
                .collect(Collectors.joining(" "));
        final String certfilelocations =
                "/var/run/secrets/kubernetes.io/serviceaccount/service-ca.crt /var/run/secrets/kubernetes.io/serviceaccount/ca.crt "
                        + certFiles;
        return new EnvVar("X509_CA_BUNDLE",
                certfilelocations, null);
    }

    private String determineKeycloaksNonStandardDbVendorName(DatabaseSchemaConnectionInfo databaseSchemaConnectionInfo) {
        if (databaseSchemaConnectionInfo.getDatabaseServiceResult().getVendor() == DbmsVendorConfig.POSTGRESQL) {
            return "postgres";
        } else {
            return databaseSchemaConnectionInfo.getDatabaseServiceResult().getVendor().getName();
        }
    }

    @Override
    public String getWebContextPath() {
        return "/auth";
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath());
    }

    @Override
    public String getVolumeMountPath() {
        if (determineStandardKeycloakImage() == StandardKeycloakImage.REDHAT_SSO) {
            return "/opt/eap/standalone/data";
        } else {
            return "/opt/jboss/keycloak/standalone/data";
        }
    }

    @Override
    public Optional<EntandoResourceRequirements> getResourceRequirementsOverride() {
        return keycloakServer.getSpec().getResourceRequirements();
    }

    @Override
    public List<EnvVar> getEnvironmentVariableOverrides() {
        return keycloakServer.getSpec().getEnvironmentVariables();
    }
}
