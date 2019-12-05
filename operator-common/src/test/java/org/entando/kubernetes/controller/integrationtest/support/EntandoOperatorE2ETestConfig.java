package org.entando.kubernetes.controller.integrationtest.support;

import java.util.Optional;
import org.entando.kubernetes.controller.EntandoOperatorConfigBase;

public final class EntandoOperatorE2ETestConfig extends EntandoOperatorConfigBase {

    private static final String ENTANDO_TEST_NAMESPACE_OVERRIDE = "entando.test.namespace.override";
    private static final String ENTANDO_TEST_NAME_SUFFIX = "entando.test.name.suffix";
    private static final String ENTANDO_INTEGRATION_TARGET_ENVIRONMENT = "entando.k8s.operator.tests.run.target";
    private static final String ENTANDO_TESTS_CERT_ROOT = "entando.k8s.operator.tests.cert.root";

    private EntandoOperatorE2ETestConfig() {
    }

    public static Optional<String> getKubernetesUsername() {
        return lookupProperty("entando.kubernetes.username");
    }

    public static Optional<String> getKubernetesPassword() {
        return lookupProperty("entando.kubernetes.password");
    }

    public static Optional<String> getKubernetesMasterUrl() {
        return lookupProperty("entando.kubernetes.master.url");
    }

    public static String getTestsCertRoot() {
        return lookupProperty(ENTANDO_TESTS_CERT_ROOT).orElse("src/test/resources/tls");
    }

    public static TestTarget getTestTarget() {
        return lookupProperty(ENTANDO_INTEGRATION_TARGET_ENVIRONMENT).map(String::toUpperCase).map(TestTarget::valueOf)
                .orElse(TestTarget.STANDALONE);
    }

    public static String calculateName(String baseName) {
        return baseName + getTestNameSuffix().map(s -> "-" + s).orElse("");
    }

    public static String calculateNameSpace(String baseName) {
        return calculateName(getTestNamespaceOverride().orElse(baseName));
    }

    public static Optional<String> getTestNamespaceOverride() {
        return lookupProperty(ENTANDO_TEST_NAMESPACE_OVERRIDE);
    }

    public static Optional<String> getTestNameSuffix() {
        return lookupProperty(ENTANDO_TEST_NAME_SUFFIX);
    }

    public enum TestTarget {
        K8S, STANDALONE
    }
}
