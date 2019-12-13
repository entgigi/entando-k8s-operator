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

package org.entando.kubernetes.model.externaldatabase;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.api.model.Doneable;
import java.util.Optional;
import org.entando.kubernetes.model.AbstractServerStatus;
import org.entando.kubernetes.model.DoneableEntandoCustomResource;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;

public class DoneableEntandoExternalDatabase extends EntandoExternalDatabaseFluent<DoneableEntandoExternalDatabase> implements
        Doneable<EntandoExternalDatabase>,
        DoneableEntandoCustomResource<DoneableEntandoExternalDatabase, EntandoExternalDatabase> {

    private final Function<EntandoExternalDatabase, EntandoExternalDatabase> function;
    private final EntandoCustomResourceStatus status;

    public DoneableEntandoExternalDatabase(Function<EntandoExternalDatabase, EntandoExternalDatabase> function) {
        this.status = new EntandoCustomResourceStatus();
        this.function = function;
    }

    public DoneableEntandoExternalDatabase(EntandoExternalDatabase resource,
            Function<EntandoExternalDatabase, EntandoExternalDatabase> function) {
        super(resource.getSpec(), resource.getMetadata());
        this.status = Optional.ofNullable(resource.getStatus()).orElse(new EntandoCustomResourceStatus());
        this.function = function;
    }

    @Override
    public EntandoExternalDatabase done() {
        return function.apply(build());
    }

    @Override
    public DoneableEntandoExternalDatabase withStatus(AbstractServerStatus status) {
        this.status.putServerStatus(status);
        return this;
    }

    @Override
    public DoneableEntandoExternalDatabase withPhase(EntandoDeploymentPhase phase) {
        status.setEntandoDeploymentPhase(phase);
        return this;
    }

    private EntandoExternalDatabase build() {
        return new EntandoExternalDatabase(super.metadata.build(), super.spec.build(), status);
    }
}
