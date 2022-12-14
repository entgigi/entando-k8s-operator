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

package org.entando.kubernetes.controller.support.client.impl;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPort;
import io.fabric8.kubernetes.api.model.networking.v1.ServiceBackendPortBuilder;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.spi.client.AbstractSupportK8SIntegrationTest;
import org.entando.kubernetes.fluentspi.TestResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@EnableRuleMigrationSupport
class DefaultIngressClientTest extends AbstractSupportK8SIntegrationTest {

    @Override
    protected String[] getNamespacesToUse() {
        return new String[]{MY_APP_NAMESPACE_1};
    }

    @Test
    void shouldRemovePathFromIngress() {
        TestResource app = newTestResource();
        Ingress myIngress = getTestIngress();
        myIngress.getMetadata().setNamespace(app.getMetadata().getNamespace());
        this.getFabric8Client().extensions().ingresses().inNamespace(app.getMetadata().getNamespace())
                .withName(myIngress.getMetadata().getName()).delete();
        Ingress deployedIngress = this.getSimpleK8SClient().ingresses().createIngress(app, myIngress);

        Assertions.assertTrue(() -> deployedIngress.getSpec().getRules().get(0).getHttp().getPaths().size() == 2);

        HTTPIngressPath ingressPath = deployedIngress.getSpec().getRules().get(0).getHttp().getPaths().get(0);
        Ingress cleanedIngress = this.getSimpleK8SClient().ingresses().removeHttpPath(deployedIngress, ingressPath);

        Assertions.assertFalse(() ->
                cleanedIngress.getSpec().getRules().get(0).getHttp().getPaths().stream()
                        .anyMatch(p -> p.getPath().equals(ingressPath.getPath())
                                && p.getBackend().getService().getPort()
                                .equals(ingressPath.getBackend().getService().getPort())
                                && p.getBackend().getService().getName()
                                .equals(ingressPath.getBackend().getService().getName())));

        Assertions.assertTrue(() -> cleanedIngress.getSpec().getRules().get(0).getHttp()
                .getPaths().size() == 1);
    }

    @Test
    @Disabled("Disabled for now, need to come back later")
    @SuppressWarnings("java:S2925")
    void shouldRemainConsistentWithManyThreads() throws JsonProcessingException, InterruptedException {
        TestResource app = newTestResource();
        Ingress myIngress = getTestIngress();
        this.getFabric8Client().extensions().ingresses().inNamespace(app.getMetadata().getNamespace())
                .withName(myIngress.getMetadata().getName()).delete();
        this.getSimpleK8SClient().ingresses().createIngress(app, myIngress);
        myIngress.getSpec().getRules().get(0).getHttp().getPaths().clear();
        final int total = 5;
        ExecutorService executor = Executors.newFixedThreadPool(total + 2);
        //When I create multiple ingresses at the same time with different paths
        for (int i = 0; i < total; i++) {
            Ingress tmp = objectMapper.readValue(objectMapper.writeValueAsString(myIngress), Ingress.class);
            tmp.getSpec().getRules().get(0).getHttp().getPaths().add(new HTTPIngressPathBuilder()
                    .withPath("/path/" + i)
                    .withNewBackend()
                    .withNewService()
                    .withName("service-for-path" + i)
                    .withPort(new ServiceBackendPortBuilder().withNumber(8080).build())
                    .endService()
                    .endBackend()
                    .build());
            executor.submit(() -> getSimpleK8SClient().ingresses().createIngress(app, tmp));
        }
        await().atMost(1, TimeUnit.MINUTES).ignoreExceptions().until(() -> {
            boolean res = getSimpleK8SClient().ingresses()
                    .loadIngress(app.getMetadata().getNamespace(), myIngress.getMetadata().getName())
                    .getSpec().getRules().get(0).getHttp().getPaths().size() < total;
            if (!res) {
                Thread.sleep(1000);
            }
            return res;
        });
        executor.shutdown();
        await().atMost(10, TimeUnit.MINUTES).ignoreExceptions().until(() ->
                executor.awaitTermination(mkTimeoutSec(60), TimeUnit.SECONDS));
        Ingress actual = getSimpleK8SClient().ingresses()
                .loadIngress(app.getMetadata().getNamespace(), myIngress.getMetadata().getName());
        //Then the paths should be consistent
        for (int i = 0; i < total; i++) {
            int finalI = i;
            assertTrue(actual.getSpec().getRules().get(0).getHttp().getPaths().stream()
                    .anyMatch(httpIngressPath -> httpIngressPath.getPath().equals("/path/" + finalI)));
        }
    }

    @Test
    void shouldAddHttpPath() {
        //Given I have an Ingress
        Ingress myIngress = getTestIngress();
        final TestResource app = newTestResource();
        myIngress.getMetadata().setNamespace(app.getMetadata().getNamespace());
        this.getFabric8Client().extensions().ingresses().inNamespace(app.getMetadata().getNamespace())
                .withName(myIngress.getMetadata().getName()).delete();
        Ingress deployedIngress = this.getSimpleK8SClient().ingresses().createIngress(app, myIngress);
        //When I add the path '/new-path' to it
        getSimpleK8SClient().ingresses().addHttpPath(deployedIngress, new HTTPIngressPathBuilder()
                .withPath("/new-path")
                .withNewBackend()
                .withNewService()
                .withName("some-service")
                .withPort(new ServiceBackendPortBuilder().withNumber(80).build())
                .endService()
                .endBackend()
                .build(), Collections.emptyMap());
        final Ingress actual = getSimpleK8SClient().ingresses()
                .loadIngress(app.getMetadata().getNamespace(), myIngress.getMetadata().getName());
        assertThat(actual.getSpec().getRules().get(0).getHttp().getPaths().get(2).getPath(), is("/new-path"));
    }

    private Ingress getTestIngress() {
        return new IngressBuilder()
                .withNewMetadata()
                .withName("my-ingress")
                .endMetadata()
                .withNewSpec()
                .addNewRule()
                .withHost("my-host-local")
                .withNewHttp()
                .addNewPath()
                .withPath("/path1")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName("path1-plugin")
                .withPort(new ServiceBackendPortBuilder().withNumber(8081).build())
                .endService()
                .endBackend()
                .endPath()
                .addNewPath()
                .withPath("/path2")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName("path2-plugin")
                .withPort(new ServiceBackendPortBuilder().withNumber(8081).build())
                .endService()
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build();
    }

}
