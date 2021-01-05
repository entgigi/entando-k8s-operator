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

package org.entando.kubernetes.controller;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.common.DockerImageInfo;

public class PropertyResolution {

    private final ConfigMap imageVersionsConfigMap;
    private final String organizationAwareKey;
    private final String imageRepostory;
    private EntandoOperatorConfigProperty overridingPropertyName;
    private String configMapKey;
    private EntandoOperatorConfigProperty fallbackPropertyName;
    private String defaultValue;
    private String providedValue;

    public PropertyResolution(ConfigMap imageVersionsConfigMap, DockerImageInfo dockerImageInfo) {
        this.imageVersionsConfigMap = imageVersionsConfigMap;
        this.organizationAwareKey = dockerImageInfo.getOrganizationAwareRepository();
        this.imageRepostory = dockerImageInfo.getRepository();
    }

    public PropertyResolution withOverridingPropertyName(EntandoOperatorConfigProperty overridingPropertyName) {
        this.overridingPropertyName = overridingPropertyName;
        return this;
    }

    public PropertyResolution withProvidedValue(String providedValue) {
        this.providedValue = providedValue;
        return this;
    }

    public PropertyResolution withConfigMapKey(String configMapKey) {
        this.configMapKey = configMapKey;
        return this;
    }

    public PropertyResolution withFallbackPropertyName(EntandoOperatorConfigProperty fallbackPropertyName) {
        this.fallbackPropertyName = fallbackPropertyName;
        return this;
    }

    public PropertyResolution withDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public String resolvePropertyValue() {
        return overridingPropertyValue()
                .orElse(valueFromConfigMap()
                        .orElse(ofNullable(providedValue)
                                .orElse(fallbackPropertyValue().orElse(defaultValue))));
    }

    private Optional<String> fallbackPropertyValue() {
        return EntandoOperatorConfigBase.lookupProperty(fallbackPropertyName);
    }

    private Optional<String> overridingPropertyValue() {
        return EntandoOperatorConfigBase.lookupProperty(overridingPropertyName);
    }

    private Optional<String> valueFromConfigMap() {
        return resolveDockerImageInfoFromConfigmapData(imageRepostory)
                .or(() -> resolveDockerImageInfoFromConfigmapData(organizationAwareKey))
                .flatMap(this::extractValueFromContent);

    }

    private Optional<String> resolveDockerImageInfoFromConfigmapData(String imageKey) {
        return ofNullable(imageVersionsConfigMap).map(ConfigMap::getData).map(data -> data.get(imageKey));
    }

    @SuppressWarnings("unchecked")
    private Optional<String> extractValueFromContent(String content) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> imageConfig = mapper.readValue(content, Map.class);
            return ofNullable(imageConfig.get(configMapKey));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

}
