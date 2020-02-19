package org.entando.kubernetes.controller.database;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.AbstractServiceResult;
import org.entando.kubernetes.controller.PodResult;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;

public class DatabaseServiceResult extends AbstractServiceResult {

    private final DbmsVendorStrategy vendor;
    private final String databaseName;
    private final String databaseSecretName;
    private String tablespace;
    private Pod pod;
    private Map<String, String> databaseParameters;

    public DatabaseServiceResult(Service service, DbmsVendorStrategy vendor, String databaseName, String databaseSecretName, Pod pod) {
        this(service, vendor, databaseName, databaseSecretName);
        this.pod = pod;
        this.databaseParameters = Collections.emptyMap();
    }

    private DatabaseServiceResult(Service service, DbmsVendorStrategy vendor, String databaseName, String databaseSecretName) {
        super(service);
        this.vendor = vendor;
        this.databaseName = databaseName;
        this.databaseSecretName = databaseSecretName;
    }

    public DatabaseServiceResult(Service service, EntandoDatabaseService databaseService) {
        this(service,
                DbmsVendorStrategy.forVendor(databaseService.getSpec().getDbms()),
                databaseService.getSpec().getDatabaseName(),
                databaseService.getSpec().getSecretName());
        this.databaseParameters = databaseService.getSpec().getJdbcParameters();
        this.tablespace = databaseService.getSpec().getTablespace().orElse(null);
    }

    public Optional<String> getTablespace() {
        return Optional.ofNullable(tablespace);
    }

    public Map<String, String> getJdbcParameters() {
        return databaseParameters;
    }

    public String getDatabaseSecretName() {
        return databaseSecretName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public DbmsVendorStrategy getVendor() {
        return vendor;
    }

    public boolean hasFailed() {
        return Optional.ofNullable(pod).map(existingPod -> PodResult.of(existingPod).hasFailed()).orElse(false);
    }

}
