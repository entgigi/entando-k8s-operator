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

package org.entando.kubernetes.model.keycloakserver;

import org.entando.kubernetes.model.common.NestedIngressingDeploymentSpecFluent;

public class NestedEntandoKeycloakServerSpecFluent<F extends EntandoKeycloakServerFluent<F>>
        extends EntandoKeycloakServerSpecFluent<NestedEntandoKeycloakServerSpecFluent<F>>
        implements NestedIngressingDeploymentSpecFluent<F, NestedEntandoKeycloakServerSpecFluent<F>> {

    private final F parentBuilder;

    NestedEntandoKeycloakServerSpecFluent(F parentBuilder, EntandoKeycloakServerSpec item) {
        super(item);
        this.parentBuilder = parentBuilder;
    }

    NestedEntandoKeycloakServerSpecFluent(F parentBuilder) {
        super();
        this.parentBuilder = parentBuilder;
    }

    @Override
    public F endSpec() {
        return and();
    }

    @Override
    public F and() {
        return parentBuilder.withSpec(this.build());
    }
}
