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

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.HashMap;
import java.util.List;
import org.entando.kubernetes.controller.EntandoImageResolver;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.creators.DeploymentCreator;
import org.entando.kubernetes.controller.database.DatabaseDeployable;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.DeploymentClientDouble;
import org.entando.kubernetes.controller.test.support.assertionhelper.ResourceRequirementsAssertionHelper;
import org.entando.kubernetes.controller.test.support.stubhelper.CustomResourceStubHelper;
import org.entando.kubernetes.controller.test.support.stubhelper.DeployableStubHelper;
import org.entando.kubernetes.model.externaldatabase.EntandoDatabaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("unit")})
public class DeploymentCreatorTest {

    private EntandoDatabaseService entandoDatabaseService = CustomResourceStubHelper.stubEntandoDatabaseService();
    private DatabaseDeployable deployable = DeployableStubHelper.stubDatabaseDeployable();

    @AfterEach
    public void cleanUp() {
        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_DEFAULT_LIMITS.getJvmSystemProperty(), "true");
    }

    @Test
    public void createDeploymentWithTrueImposeResourceLimitsWillSetResourceLimitsOnCreatedDeployment() {

        ResourceRequirements resources = executeCreateDeploymentTest();
        List<Quantity> quantities = DeployableStubHelper.stubResourceQuantities(deployable);

        ResourceRequirementsAssertionHelper.assertQuantities(quantities, resources);
    }


    @Test
    public void createDeploymentWithFalseImposeResourceLimitsWillNotSetResourceLimitsOnCreatedDeployment() {

        System.setProperty(EntandoOperatorConfigProperty.ENTANDO_K8S_OPERATOR_IMPOSE_DEFAULT_LIMITS.getJvmSystemProperty(), "false");

        ResourceRequirements resources = executeCreateDeploymentTest();

        assertTrue(resources.getLimits().isEmpty());
        assertTrue(resources.getRequests().isEmpty());
    }

    /**
     * executes tests of types CreateDeploymentTest.
     * @return the ResourceRequirements of the first container of the resulting Deployment
     */
    private ResourceRequirements executeCreateDeploymentTest() {

        DeploymentClientDouble deploymentClientDouble = new DeploymentClientDouble(new HashMap<>());
        DeploymentCreator deploymentCreator = new DeploymentCreator(entandoDatabaseService);

        Deployment actual = deploymentCreator.createDeployment(
                new EntandoImageResolver(null),
                deploymentClientDouble,
                deployable);

        return actual.getSpec().getTemplate().getSpec().getContainers().get(0).getResources();
    }
}
