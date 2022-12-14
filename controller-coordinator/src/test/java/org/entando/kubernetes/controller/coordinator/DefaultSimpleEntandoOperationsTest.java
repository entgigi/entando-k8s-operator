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

import static io.qameta.allure.Allure.attachment;
import static io.qameta.allure.Allure.step;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.coordinator.common.CoordinatorTestUtils;
import org.entando.kubernetes.controller.spi.client.SerializedEntandoResource;
import org.entando.kubernetes.controller.spi.common.PodResult;
import org.entando.kubernetes.controller.spi.common.PodResult.State;
import org.entando.kubernetes.fluentspi.TestResource;
import org.entando.kubernetes.test.common.ValueHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@SuppressWarnings("CodeBlock2Expr")
@Tags({@Tag("adapter"), @Tag("pre-deployment"), @Tag("integration")})
@Feature("As a controller-coordinator developer, I would like perform common operations on a specific resource using a simple "
        + "interface to reduce the learning curve")
@EnableRuleMigrationSupport
class DefaultSimpleEntandoOperationsTest extends ControllerCoordinatorAdapterTestBase {

    public static final String MY_POD = "my-pod";
    DefaultSimpleEntandoOperations myClient;

    protected NamespacedKubernetesClient getNamespacedKubernetesClient() {
        final NamespacedKubernetesClient c = new DefaultKubernetesClient().inNamespace(MY_APP_NAMESPACE_1);
        registerCrdResource(c, "testresources.test.org.crd.yaml");
        return c;
    }

    public DefaultSimpleEntandoOperations getMyOperations() {
        this.myClient = Objects.requireNonNullElseGet(this.myClient,
                () -> {
                    final NamespacedKubernetesClient client = getNamespacedKubernetesClient();
                    final CustomResourceDefinitionContext definitionContext =
                            CustomResourceDefinitionContext.fromCustomResourceType(TestResource.class);
                    return new DefaultSimpleEntandoOperations(
                            client,
                            definitionContext,
                            client.customResource(definitionContext),
                            true);
                });
        return this.myClient;
    }

    @Test
    @Description("Should delete pods and wait until they have been successfully deleted ")
    void shouldRemoveSuccessfullyCompletedPods() {
        awaitDefaultToken(MY_APP_NAMESPACE_1);
        final TestResource testResource = new TestResource().withNames(MY_APP_NAMESPACE_1, "my-test-resource");
        step("Given I have a TestResource", () -> attachment("TestResource", objectMapper.writeValueAsString(testResource)));
        step("And I have started a service pod with the label associated with this resource that will complete after 1 second", () -> {
            final Pod startedPod = getFabric8Client().pods().inNamespace(MY_APP_NAMESPACE_1).create(new PodBuilder()
                    .withNewMetadata()
                    .withName(MY_POD)
                    .withNamespace(MY_APP_NAMESPACE_1)
                    .addToLabels(CoordinatorUtils.podLabelsFor(testResource))
                    .endMetadata()
                    .withNewSpec()
                    .addNewContainer()
                    .withImage("busybox")
                    .withName("busybox")
                    .withArgs("/bin/sh", "-c", "sleep 3")
                    .endContainer()
                    .withRestartPolicy("Never")
                    .endSpec()
                    .build());

            attachment("Started Pod", objectMapper.writeValueAsString(startedPod));
        });
        step("And I have waited for the pod to be ready", () -> {
            await().ignoreExceptions().atMost(60, TimeUnit.SECONDS).until(
                    () -> PodResult.of(getPodInNamespace(MY_APP_NAMESPACE_1, MY_POD)).getState() != State.CREATING);

            attachment("Started Pod", objectMapper.writeValueAsString(getPodInNamespace(MY_APP_NAMESPACE_1, MY_POD)));
        });
        step("When I remove all the successfully completed pods", () -> {
            getMyOperations().removeSuccessfullyCompletedPods(CoordinatorTestUtils.toSerializedResource(testResource));
        });
        step("Then that pod will be absent immediately after the call finished", () -> {
            assertThat(getPodInNamespace(MY_APP_NAMESPACE_1, MY_POD)).isNull();
        });
    }

    private Pod getPodInNamespace(String ns, String podName) {
        return getFabric8Client().pods().inNamespace(ns).withName(podName).fromServer().get();
    }

    @Test
    @Description("Should add and remove annotations")
    void shouldAndAndRemoveAnnotations() {
        ValueHolder<SerializedEntandoResource> resource = new ValueHolder<>();
        step("Given I have a TestResource", () -> {
            final TestResource testResource = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_1, "my-test-resource"));
            resource.set(CoordinatorTestUtils.toSerializedResource(testResource));
            attachment("TestResource", objectMapper.writeValueAsString(resource.get()));
        });

        step("And I have added the annotation 'entando.org/processed-by-version=6.3.0' to it", () -> {
            resource.set(getMyOperations().putAnnotation(resource.get(), AnnotationNames.PROCESSED_BY_OPERATOR_VERSION.getName(), "6.3.0"));
            assertThat(CoordinatorUtils.resolveAnnotation(resource.get(), AnnotationNames.PROCESSED_BY_OPERATOR_VERSION))
                    .contains("6.3.0");
            attachment("TestResource", objectMapper.writeValueAsString(resource.get()));
        });
        step("When I remove the annotation 'entando.org/processed-by-version=6.3.0' from it it", () -> {
            resource.set(getMyOperations().removeAnnotation(resource.get(), AnnotationNames.PROCESSED_BY_OPERATOR_VERSION.getName()));
            attachment("TestResource", objectMapper.writeValueAsString(resource.get()));
        });
        step("The annotation is no longer present on the resource", () -> {
            assertThat(resource.get().getMetadata().getAnnotations())
                    .doesNotContainKey(AnnotationNames.PROCESSED_BY_OPERATOR_VERSION.getName());
        });
    }

    @Test
    @Description("Should list in any namespace")
    void shouldListInAnyNamespace() {
        step(format("Given I have created two TestResource in namespaces %s and %s", MY_APP_NAMESPACE_1, MY_APP_NAMESPACE_1 + "1"), () -> {
            final TestResource testResource1 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_1, "my-test-resource1"));
            attachment("TestResource 1", objectMapper.writeValueAsString(testResource1));
            final TestResource testResource2 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_2, "my-test-resource2"));
            attachment("TestResource 2", objectMapper.writeValueAsString(testResource2));
        });
        List<SerializedEntandoResource> actual = new ArrayList<>();
        step("When I list TestResources in any namespace", () -> {
            actual.addAll(getMyOperations().inAnyNamespace().list());
        });
        step("Then both resources are found", () -> {
            assertThat(actual).anyMatch(r -> r.getMetadata().getName().equals("my-test-resource1"));
            assertThat(actual).anyMatch(r -> r.getMetadata().getName().equals("my-test-resource2"));
            attachment("TestResources", objectMapper.writeValueAsString(actual));
        });
    }

    @Test
    @Description("Should list in a single namespace")
    void shouldListInSingleNamespace() {
        step(format("Given I have created two TestResource in namespaces %s and %s", MY_APP_NAMESPACE_1, MY_APP_NAMESPACE_1 + "1"), () -> {
            final TestResource testResource1 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_1, "my-test-resource1"));
            attachment("TestResource 1", objectMapper.writeValueAsString(testResource1));
            final TestResource testResource2 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_2, "my-test-resource2"));
            attachment("TestResource 2", objectMapper.writeValueAsString(testResource2));
        });
        List<SerializedEntandoResource> actual = new ArrayList<>();
        step("When I list TestResources in any namespace", () -> {
            actual.addAll(getMyOperations().inNamespace(MY_APP_NAMESPACE_1).list());
        });
        step("Then both resources are found", () -> {
            await().atMost(10, TimeUnit.SECONDS).ignoreExceptions().until(() -> actual.size() == 1);
            assertThat(actual).size().isEqualTo(1);
            assertThat(actual).anyMatch(r -> r.getMetadata().getName().equals("my-test-resource1"));
            attachment("TestResource 1", objectMapper.writeValueAsString(actual));
        });
    }

    @Test
    @Description("Should watch in any namespace")
    void shouldWatchInAnyNamespace() {
        final Map<String, SerializedEntandoResource> resourcesObserved = new ConcurrentHashMap<>();
        step("Given I have registered a Watcher for TestResource in any namespace", () -> {
            getMyOperations().inAnyNamespace().watch(
                    (action, serializedEntandoResource) -> resourcesObserved
                            .put(serializedEntandoResource.getMetadata().getName(), serializedEntandoResource));
        });
        step(format("When  I create two TestResource in namespaces %s and %s", MY_APP_NAMESPACE_1, MY_APP_NAMESPACE_1 + "1"), () -> {
            final TestResource testResource1 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_1, "my-test-resource1"));
            attachment("TestResource 1", objectMapper.writeValueAsString(testResource1));
            final TestResource testResource2 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_2, "my-test-resource2"));
            attachment("TestResource 2", objectMapper.writeValueAsString(testResource2));
        });
        step("Then both resources were observed", () -> {
            await().atMost(10, TimeUnit.SECONDS).ignoreExceptions().until(() -> resourcesObserved.containsKey("my-test-resource2"));
            assertThat(resourcesObserved).containsKeys("my-test-resource1", "my-test-resource2");
            attachment("TestResources", objectMapper.writeValueAsString(resourcesObserved));
        });
    }

    @Test
    @Description("Should watch in a single namespace")
    void shouldWatchInSingleNamespace() {
        final Map<String, SerializedEntandoResource> resourcesObserved = new ConcurrentHashMap<>();
        step(format("Given I have registered a Watcher for TestResource in namespace %s", MY_APP_NAMESPACE_2), () -> {
            getMyOperations().inNamespace(MY_APP_NAMESPACE_2).watch(
                    (action, serializedEntandoResource) -> resourcesObserved
                            .put(serializedEntandoResource.getMetadata().getName(), serializedEntandoResource));
        });
        step(format("When  I create two TestResource in namespaces %s and %s", MY_APP_NAMESPACE_1, MY_APP_NAMESPACE_1 + "1"), () -> {
            final TestResource testResource1 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_1, "my-test-resource1"));
            attachment("TestResource 1", objectMapper.writeValueAsString(testResource1));
            final TestResource testResource2 = createTestResource(
                    new TestResource().withNames(MY_APP_NAMESPACE_2, "my-test-resource2"));
            attachment("TestResource 2", objectMapper.writeValueAsString(testResource2));
        });
        step("Then both resources were observed", () -> {
            await().atMost(10, TimeUnit.SECONDS).ignoreExceptions().until(() -> resourcesObserved.containsKey("my-test-resource2"));
            assertThat(resourcesObserved).containsKey("my-test-resource2");
            assertThat(resourcesObserved).doesNotContainKey("my-test-resource1");
            attachment("TestResources", objectMapper.writeValueAsString(resourcesObserved));
        });
    }

}
