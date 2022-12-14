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

package org.entando.kubernetes.model.link;

import io.fabric8.kubernetes.api.builder.Fluent;
import io.fabric8.kubernetes.api.builder.Nested;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import org.entando.kubernetes.model.common.EntandoFluent;

public class EntandoAppPluginLinkFluent<A extends EntandoAppPluginLinkFluent<A>> extends EntandoFluent<A> implements Fluent<A> {

    protected EntandoAppPluginLinkSpecBuilder spec;

    protected EntandoAppPluginLinkFluent() {
        this(new ObjectMetaBuilder(), new EntandoAppPluginLinkSpecBuilder());
    }

    protected EntandoAppPluginLinkFluent(EntandoAppPluginLinkSpec spec, ObjectMeta objectMeta) {
        this(new ObjectMetaBuilder(objectMeta), new EntandoAppPluginLinkSpecBuilder(spec));
    }

    private EntandoAppPluginLinkFluent(ObjectMetaBuilder metadata, EntandoAppPluginLinkSpecBuilder spec) {
        super(metadata);
        this.spec = spec;
    }

    @SuppressWarnings("unchecked")
    public NestedEntandoAppPluginLinkSpecFluent<A> editSpec() {
        return new NestedEntandoAppPluginLinkSpecFluent<>((A) this, this.spec.build());
    }

    @SuppressWarnings("unchecked")
    public NestedEntandoAppPluginLinkSpecFluent<A> withNewSpec() {
        return new NestedEntandoAppPluginLinkSpecFluent<>((A) this);
    }

    @SuppressWarnings("unchecked")
    public A withSpec(EntandoAppPluginLinkSpec spec) {
        this.spec = new EntandoAppPluginLinkSpecBuilder(spec);
        return (A) this;
    }

    public static class NestedEntandoAppPluginLinkSpecFluent<N extends EntandoAppPluginLinkFluent> extends
            EntandoAppPluginLinkSpecFluent<NestedEntandoAppPluginLinkSpecFluent<N>> implements
            Nested<N> {

        private final N parentBuilder;

        NestedEntandoAppPluginLinkSpecFluent(N parentBuilder, EntandoAppPluginLinkSpec item) {
            super(item);
            this.parentBuilder = parentBuilder;
        }

        public NestedEntandoAppPluginLinkSpecFluent(N parentBuilder) {
            super();
            this.parentBuilder = parentBuilder;
        }

        @Override
        @SuppressWarnings("unchecked")
        public N and() {
            return (N) parentBuilder.withSpec(this.build());
        }

        public N endSpec() {
            return this.and();
        }
    }

}
