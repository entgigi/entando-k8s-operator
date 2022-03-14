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

package org.entando.kubernetes.controller.plugin;

import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvider;
import org.entando.kubernetes.controller.spi.capability.CapabilityProvisioningResult;
import org.entando.kubernetes.controller.spi.client.KubernetesClientForControllers;
import org.entando.kubernetes.controller.spi.command.DeploymentProcessor;
import org.entando.kubernetes.controller.spi.common.EntandoControllerException;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ProvidedDatabaseCapability;
import org.entando.kubernetes.controller.spi.container.ProvidedSsoCapability;
import org.entando.kubernetes.controller.spi.deployable.SsoConnectionInfo;
import org.entando.kubernetes.controller.spi.result.DatabaseConnectionInfo;
import org.entando.kubernetes.model.capability.CapabilityRequirementBuilder;
import org.entando.kubernetes.model.capability.CapabilityScope;
import org.entando.kubernetes.model.capability.StandardCapability;
import org.entando.kubernetes.model.capability.StandardCapabilityImplementation;
import org.entando.kubernetes.model.common.DbmsVendor;
import org.entando.kubernetes.model.common.EntandoControllerFailure;
import org.entando.kubernetes.model.common.ServerStatus;
import org.entando.kubernetes.model.plugin.EntandoPlugin;
import picocli.CommandLine;

@CommandLine.Command()
public class EntandoPluginController implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(EntandoPluginController.class.getName());
    private final KubernetesClientForControllers k8sClient;
    private final CapabilityProvider capabilityProvider;
    private final DeploymentProcessor deploymentProcessor;
    private EntandoPlugin entandoPlugin;

    @Inject
    public EntandoPluginController(KubernetesClientForControllers k8sClient, DeploymentProcessor deploymentProcessor,
            CapabilityProvider capabilityProvider) {
        this.k8sClient = k8sClient;
        this.capabilityProvider = capabilityProvider;
        this.deploymentProcessor = deploymentProcessor;
    }

    @Override
    public void run() {
        this.entandoPlugin = (EntandoPlugin) k8sClient.resolveCustomResourceToProcess(Collections.singletonList(EntandoPlugin.class));
        EntandoPluginServerDeployable deployable;
        try {
            this.entandoPlugin = k8sClient.deploymentStarted(entandoPlugin);
            final DatabaseConnectionInfo dbConnectionInfo = provideDatabaseIfRequired();
            final SsoConnectionInfo ssoConnectionInfo = provideSso();
            deployable = new EntandoPluginServerDeployable(dbConnectionInfo,
                    ssoConnectionInfo, entandoPlugin);
            this.deploymentProcessor.processDeployable(deployable, calculateDbAwareTimeout());
            this.entandoPlugin = k8sClient.deploymentEnded(entandoPlugin);
        } catch (Exception e) {
            try {
                entandoPlugin = k8sClient.deploymentFailed(entandoPlugin, e, NameUtils.MAIN_QUALIFIER);
                LOGGER.log(Level.SEVERE, e, () -> format("EntandoPluginController failed:%n%s",
                        entandoPlugin.getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER)
                                .flatMap(ServerStatus::getEntandoControllerFailure)
                                .orElseThrow(IllegalStateException::new)
                                .getDetailMessage()));
            } catch (Exception ignored) {
                LOGGER.log(Level.SEVERE, e, () -> "EntandoPluginController failed: <<UNABLE TO EXTRACT THE STATE>>");
            }
        }
        var failedServerStatus = entandoPlugin.getStatus().findFailedServerStatus();
        failedServerStatus.flatMap(ServerStatus::getEntandoControllerFailure).ifPresent(s -> {
            printFailureReport(s);
            cleanupFailedPluginInstallation(failedServerStatus.get());
            throw new CommandLine.ExecutionException(new CommandLine(this),
                    "Error starting the plugin pod");
        });
    }

    private void cleanupFailedPluginInstallation(ServerStatus failedServerStatus) {
        var dpn = failedServerStatus.getDeploymentName().orElse("");
        if (dpn.length() == 0) {
            throw new CommandLine.ExecutionException(new CommandLine(this),
                    "Unable to extract from the custom resource the status of the failed server (C1)");
        }
        var ns = entandoPlugin.getMetadata().getNamespace();
        k8sClient.getDeploymentByName(dpn, ns).scale(0);
    }

    static final String STDOUT_SEPARATOR;

    static {
        var tmp = "##########";
        STDOUT_SEPARATOR = tmp + tmp + tmp + tmp + tmp + tmp + tmp + tmp + tmp + tmp + tmp + tmp;
    }

    private void printFailureReport(EntandoControllerFailure s) {
        try {
            final var name = s.getFailedObjectName();
            final var ns = s.getFailedObjectNamespace();
            directPrint("");
            directPrint(STDOUT_SEPARATOR);
            directPrint("### ERROR STARTING THE PLUGIN POD");
            directPrint("### the plugin pod \"" + ns + "/" + name + "\" failed to start");
            var failedPod = k8sClient.getPodByName(name, ns);
            directPrint("### This is its log:");
            directPrint("");
            directPrint(SerializationUtils.getMapper().writeValueAsString(failedPod.getLog()));
            directPrint("");
            directPrint(STDOUT_SEPARATOR);
            directPrint(STDOUT_SEPARATOR);
            directPrint("");
        } catch (JsonProcessingException e) {
            directPrint("<<Unable to convert the failed pod data to yaml>>");
        } catch (Exception t) {
            directPrint("<<Unable to extract the failed pod log>>");
        }
    }

    private static void directPrint(String str) {
        System.err.println(str); //NOSONAR
    }

    private int calculateDbAwareTimeout() {
        final int timeoutForDbAware;
        if (requiresDbmsService(entandoPlugin.getSpec().getDbms().orElse(DbmsVendor.NONE))) {
            timeoutForDbAware =
                    EntandoOperatorSpiConfig.getPodCompletionTimeoutSeconds()
                            + EntandoOperatorSpiConfig.getPodReadinessTimeoutSeconds();
        } else {
            timeoutForDbAware = EntandoOperatorSpiConfig.getPodReadinessTimeoutSeconds();
        }
        return timeoutForDbAware;
    }

    private ProvidedDatabaseCapability provideDatabaseIfRequired() throws TimeoutException {
        final DbmsVendor dbmsVendor = entandoPlugin.getSpec().getDbms().orElse(DbmsVendor.NONE);
        if (requiresDbmsService(dbmsVendor)) {
            final CapabilityProvisioningResult capabilityResult = capabilityProvider
                    .provideCapability(entandoPlugin, new CapabilityRequirementBuilder()
                            .withCapability(StandardCapability.DBMS)
                            .withImplementation(StandardCapabilityImplementation.valueOf(dbmsVendor.name()))
                            .withResolutionScopePreference(CapabilityScope.NAMESPACE, CapabilityScope.DEDICATED, CapabilityScope.CLUSTER)
                            .build(), 180);
            capabilityResult.getProvidedCapability().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).ifPresent(s ->
                    this.entandoPlugin = this.k8sClient.updateStatus(entandoPlugin, new ServerStatus(NameUtils.DB_QUALIFIER, s)));
            capabilityResult.getControllerFailure().ifPresent(f -> {
                throw new EntandoControllerException(format("Could not prepare database for EntandoPlugin %s/%s%n%s", entandoPlugin
                                .getMetadata().getNamespace(), entandoPlugin
                                .getMetadata().getName(),
                        f.getDetailMessage()));
            });
            return new ProvidedDatabaseCapability(capabilityResult);
        } else {
            return null;
        }
    }

    private boolean requiresDbmsService(DbmsVendor dbmsVendor) {
        return !Set.of(DbmsVendor.NONE, DbmsVendor.EMBEDDED).contains(dbmsVendor);
    }

    private ProvidedSsoCapability provideSso() throws TimeoutException {
        final CapabilityProvisioningResult capabilityResult = capabilityProvider
                .provideCapability(entandoPlugin, new CapabilityRequirementBuilder()
                        .withCapability(StandardCapability.SSO)
                        .withPreferredDbms(determineDbmsForSso())
                        .withPreferredIngressHostName(entandoPlugin.getSpec().getIngressHostName().orElse(null))
                        .withPreferredTlsSecretName(entandoPlugin.getSpec().getTlsSecretName().orElse(null))
                        .withResolutionScopePreference(CapabilityScope.NAMESPACE, CapabilityScope.CLUSTER)
                        .build(), 240);
        capabilityResult.getProvidedCapability().getStatus().getServerStatus(NameUtils.MAIN_QUALIFIER).ifPresent(s ->
                this.entandoPlugin = this.k8sClient.updateStatus(entandoPlugin, new ServerStatus(NameUtils.SSO_QUALIFIER, s)));
        capabilityResult.getControllerFailure().ifPresent(f -> {
            throw new EntandoControllerException(format("Could not prepare SSO for EntandoPlugin %s/%s%n%s", entandoPlugin
                            .getMetadata().getNamespace(), entandoPlugin
                            .getMetadata().getName(),
                    f.getDetailMessage()));
        });
        return new ProvidedSsoCapability(capabilityResult);
    }

    private DbmsVendor determineDbmsForSso() {
        final DbmsVendor dbmsVendor = entandoPlugin.getSpec().getDbms().orElse(DbmsVendor.NONE);
        if (dbmsVendor == DbmsVendor.NONE) {
            return DbmsVendor.EMBEDDED;
        }
        return dbmsVendor;
    }
}
