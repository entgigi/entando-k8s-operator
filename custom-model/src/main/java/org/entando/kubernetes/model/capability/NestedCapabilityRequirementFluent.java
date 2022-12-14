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

package org.entando.kubernetes.model.capability;

import io.fabric8.kubernetes.api.builder.Nested;

//This will be compliant again once we remove EntandoClusterInfrastructure
@SuppressWarnings("java:S110")
public class NestedCapabilityRequirementFluent<N extends ProvidedCapabilityFluent<N>>
        extends CapabilityRequirementFluent<NestedCapabilityRequirementFluent<N>> implements Nested<N> {

    private final N parentBuilder;

    NestedCapabilityRequirementFluent(N parentBuilder, CapabilityRequirement item) {
        super(item);
        this.parentBuilder = parentBuilder;
    }

    public NestedCapabilityRequirementFluent(N parentBuilder) {
        super();
        this.parentBuilder = parentBuilder;
    }

    @Override
    public N and() {
        return parentBuilder.withSpec(this.build());
    }

    public N endSpec() {
        return this.and();
    }
}
