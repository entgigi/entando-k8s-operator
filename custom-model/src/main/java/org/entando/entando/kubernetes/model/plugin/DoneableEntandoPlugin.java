package org.entando.entando.kubernetes.model.plugin;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import org.entando.entando.kubernetes.model.AbstractServerStatus;
import org.entando.entando.kubernetes.model.DbServerStatus;
import org.entando.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.entando.kubernetes.model.WebServerStatus;

public class DoneableEntandoPlugin extends CustomResourceDoneable<EntandoPlugin> implements
        DoneableEntandoCustomResource<DoneableEntandoPlugin, EntandoPlugin> {

    private final EntandoPlugin resource;

    public DoneableEntandoPlugin(EntandoPlugin resource, Function function) {
        super(resource, function);
        this.resource = resource;
    }

    public DoneableEntandoPlugin addNewConnectionConfigName(String name) {
        resource.getSpec().getConnectionConfigNames().add(name);
        return this;
    }

    @Override
    public DoneableEntandoPlugin withStatus(AbstractServerStatus status) {
        if (status instanceof DbServerStatus) {
            this.resource.getStatus().addDbServerStatus((DbServerStatus) status);
        } else {
            this.resource.getStatus().addJeeServerStatus((WebServerStatus) status);
        }
        return this;
    }

    @Override
    public DoneableEntandoPlugin withPhase(EntandoDeploymentPhase phase) {
        resource.getStatus().setEntandoDeploymentPhase(phase);
        return this;
    }
}
