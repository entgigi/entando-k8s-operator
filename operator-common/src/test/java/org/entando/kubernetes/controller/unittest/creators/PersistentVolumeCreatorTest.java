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

package org.entando.kubernetes.controller.unittest.creators;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import java.util.HashMap;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.common.examples.SamplePublicIngressingDbAwareDeployable;
import org.entando.kubernetes.controller.creators.PersistentVolumeClaimCreator;
import org.entando.kubernetes.controller.inprocesstest.InProcessTestUtil;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.PersistentVolumentClaimClientDouble;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
public class PersistentVolumeCreatorTest implements InProcessTestUtil {

    private EntandoKeycloakServer entandoKeycloakServer = newEntandoKeycloakServer();
    private SamplePublicIngressingDbAwareDeployable<EntandoKeycloakServer> deployable = new SamplePublicIngressingDbAwareDeployable<>(
            entandoKeycloakServer, null, null);

    @AfterEach
    @BeforeEach
    public void cleanUp() {
        System.getProperties().remove(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DISABLE_PVC_GC.getJvmSystemProperty());
    }

    @Test
    public void createPersistentVolumeClaimWithoutGarbageCollection() {

        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DISABLE_PVC_GC.getJvmSystemProperty(), "false");

        PersistentVolumeClaim persistentVolumeClaim = executeCreateDeploymentTest();

        assertThat(persistentVolumeClaim.getMetadata().getOwnerReferences().size(), is(0));
    }

    @Test
    public void createPersistentVolumeClaimWithGarbageCollection() {

        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_DISABLE_PVC_GC.getJvmSystemProperty(), "true");

        PersistentVolumeClaim persistentVolumeClaim = executeCreateDeploymentTest();

        assertThat(persistentVolumeClaim.getMetadata().getOwnerReferences().size(), is(1));
    }

    /**
     * executes tests of types CreateDeploymentTest.
     *
     * @return the ResourceRequirements of the first container of the resulting Deployment
     */
    private PersistentVolumeClaim executeCreateDeploymentTest() {

        PersistentVolumentClaimClientDouble persistentVolumentClaimClientDouble = new PersistentVolumentClaimClientDouble(new HashMap<>());
        PersistentVolumeClaimCreator persistentVolumeClaimCreator = new PersistentVolumeClaimCreator(entandoKeycloakServer);

        persistentVolumeClaimCreator.createPersistentVolumeClaimsFor(persistentVolumentClaimClientDouble, deployable);

        return persistentVolumentClaimClientDouble.loadPersistentVolumeClaim(entandoKeycloakServer, "my-keycloak-server-pvc");
    }
}
