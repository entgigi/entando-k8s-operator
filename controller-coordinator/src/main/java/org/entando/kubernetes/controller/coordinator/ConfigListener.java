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

package org.entando.kubernetes.controller.coordinator;

import io.fabric8.kubernetes.api.model.ConfigMap;
import org.entando.kubernetes.controller.spi.common.EntandoOperatorConfigBase;

public class ConfigListener implements RestartingWatcher<ConfigMap> {

    private final SimpleKubernetesClient client;

    public ConfigListener(SimpleKubernetesClient client) {
        this.client = client;
        getRestartingAction().run();
    }

    @Override
    public void eventReceived(Action action, ConfigMap resource) {
        EntandoOperatorConfigBase.setConfigMap(resource);
    }

    @Override
    public Runnable getRestartingAction() {
        return () -> client.watchControllerConfigMap(CoordinatorUtils.ENTANDO_OPERATOR_CONFIG, this);
    }

}
