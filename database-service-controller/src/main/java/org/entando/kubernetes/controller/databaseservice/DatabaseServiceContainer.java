package org.entando.kubernetes.controller.databaseservice;

import static java.util.Optional.ofNullable;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.entando.kubernetes.controller.spi.common.DbmsDockerVendorStrategy;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorSpiConfig;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.container.DockerImageInfo;
import org.entando.kubernetes.controller.spi.container.HasHealthCommand;
import org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer;
import org.entando.kubernetes.controller.spi.container.ServiceBackingContainer;
import org.entando.kubernetes.model.common.EntandoResourceRequirements;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class DatabaseServiceContainer implements ConfigurableResourceContainer, ServiceBackingContainer,
        PersistentVolumeAwareContainer, HasHealthCommand {

    public static final int MAX_STARTUP_TIME = 90;
    private final EntandoDatabaseService entandoDatabaseService;
    private final DbmsDockerVendorStrategy dbmsVendorDockerStrategy;
    private final DatabaseVariableInitializer variableInitializer;
    private final Integer portOverride;

    public DatabaseServiceContainer(EntandoDatabaseService entandoDatabaseService, DatabaseVariableInitializer variableInitializer,
            DbmsDockerVendorStrategy dbmsVendor, Integer portOverride) {
        this.variableInitializer = variableInitializer;
        this.dbmsVendorDockerStrategy = dbmsVendor;
        this.portOverride = portOverride;
        this.entandoDatabaseService = entandoDatabaseService;
    }

    @Override
    public Optional<EntandoResourceRequirements> getResourceRequirementsOverride() {
        return entandoDatabaseService.getSpec().getResourceRequirements();
    }

    @Override
    public Optional<String> getAccessMode() {
        return Optional.of("ReadWriteOnce");
    }

    @Override
    public Optional<String> getStorageClass() {
        return this.entandoDatabaseService.getSpec().getStorageClass().or(EntandoOperatorSpiConfig::getDefaultNonClusteredStorageClass);
    }

    @Override
    public Optional<Integer> getMaximumStartupTimeSeconds() {
        return Optional.of(MAX_STARTUP_TIME);
    }

    @Override
    public DockerImageInfo getDockerImageInfo() {
        var key = "";
        switch (dbmsVendorDockerStrategy) {
            case CENTOS_MYSQL:
                key = "entando/mysql-80-centos7";
                break;
            case CENTOS_POSTGRESQL:
                key = "entando/postgresql-12-centos7";
                break;
            case RHEL_MYSQL:
                key = "entando/rhel8-mysql-80";
                break;
            case RHEL_POSTGRESQL:
                key = "entando/rhel8-postgresql-12";
                break;
            default:
                return new DockerImageInfo(dbmsVendorDockerStrategy.getImageRepository());
        }
        return new DockerImageInfo(key);
    }

    @Override
    public String getNameQualifier() {
        return NameUtils.DB_NAME_QUALIFIER;
    }

    @Override
    public int getPrimaryPort() {
        return ofNullable(portOverride).orElse(dbmsVendorDockerStrategy.getPort());
    }

    @Override
    public int getMemoryLimitMebibytes() {
        return dbmsVendorDockerStrategy.getDefaultMemoryLimitMebibytes();
    }

    @Override
    public String getVolumeMountPath() {
        return dbmsVendorDockerStrategy.getVolumeMountPath();
    }

    @Override
    public String getHealthCheckCommand() {
        return dbmsVendorDockerStrategy.getHealthCheck();
    }

    @Override
    public List<EnvVar> getEnvironmentVariables() {
        List<EnvVar> vars = new ArrayList<>();
        variableInitializer.addEnvironmentVariables(vars);
        return vars;
    }
}

