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

package org.entando.kubernetes.controller.coordinator.inprocesstests;

import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.coordinator.EntandoResourceObserver;
import org.entando.kubernetes.controller.coordinator.SimpleEntandoOperations;
import org.entando.kubernetes.controller.support.client.doubles.AbstractK8SClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.NamespaceDouble;
import org.entando.kubernetes.model.common.EntandoCustomResource;

public class SimpleEntandoOperationsDouble extends AbstractK8SClientDouble implements SimpleEntandoOperations {

    private final CustomResourceDefinitionContext definitionContext;
    String namespace;

    public SimpleEntandoOperationsDouble(ConcurrentHashMap<String, NamespaceDouble> namespaces,
            CustomResourceDefinitionContext definitionContext) {
        super(namespaces);
        this.definitionContext = definitionContext;

    }

    @Override
    public SimpleEntandoOperations inNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    @Override
    public SimpleEntandoOperations inAnyNamespace() {
        this.namespace = null;
        return this;
    }

    @Override
    public void watch(EntandoResourceObserver rldEntandoResourceObserver) {

    }

    @Override
    public List<EntandoCustomResource> list() {
        if (namespace == null) {
            return getNamespaces().values().stream()
                    .flatMap(namespaceDouble -> namespaceDouble.getCustomResources(definitionContext.getKind()).values().stream()).collect(
                            Collectors.toList());
        } else {
            return new ArrayList<>(getNamespace(namespace).getCustomResources(definitionContext.getKind()).values());
        }
    }

    @Override
    public EntandoCustomResource removeAnnotation(EntandoCustomResource r, String name) {
        r.getMetadata().getAnnotations().remove(name);

        return r;
    }

    @Override
    public EntandoCustomResource putAnnotation(EntandoCustomResource r, String name, String value) {
        r.getMetadata().getAnnotations().put(name, value);
        return r;
    }

    @Override
    public void removeSuccessfullyCompletedPods(EntandoCustomResource resource) {

    }

    @Override
    public String getControllerNamespace() {
        return namespace;
    }
}
