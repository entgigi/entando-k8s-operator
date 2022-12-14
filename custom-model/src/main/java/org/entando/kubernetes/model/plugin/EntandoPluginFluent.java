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

package org.entando.kubernetes.model.plugin;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.entando.kubernetes.model.common.EntandoFluent;
import org.entando.kubernetes.model.common.EntandoIngressingDeploymentBaseFluent;

public class EntandoPluginFluent<F extends EntandoPluginFluent<F>>
        extends EntandoFluent<F>
        implements EntandoIngressingDeploymentBaseFluent<F, NestedEntandoPluginSpecFluent<F>> {

    protected EntandoPluginSpecBuilder spec;

    protected EntandoPluginFluent() {
        this(new ObjectMetaBuilder(), new EntandoPluginSpecBuilder());
    }

    protected EntandoPluginFluent(EntandoPluginSpec spec, ObjectMeta objectMeta) {
        this(new ObjectMetaBuilder(objectMeta), new EntandoPluginSpecBuilder(spec));
    }

    private EntandoPluginFluent(ObjectMetaBuilder metadata, EntandoPluginSpecBuilder spec) {
        super(metadata);
        this.spec = spec;
    }

    public NestedEntandoPluginSpecFluent<F> editSpec() {
        return new NestedEntandoPluginSpecFluent<>(thisAsF(), this.spec.build());
    }

    public NestedEntandoPluginSpecFluent<F> withNewSpec() {
        return new NestedEntandoPluginSpecFluent<>(thisAsF());
    }

    public F withSpec(EntandoPluginSpec spec) {
        this.spec = new EntandoPluginSpecBuilder(spec);
        return thisAsF();
    }

}
