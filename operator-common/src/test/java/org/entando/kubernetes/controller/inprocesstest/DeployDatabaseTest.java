package org.entando.kubernetes.controller.inprocesstest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimStatus;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.common.example.TestServerController;
import org.entando.kubernetes.controller.inprocesstest.argumentcaptors.NamedArgumentCaptor;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.inprocesstest.k8sclientdouble.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.keycloakserver.DoneableKeycloakServer;
import org.entando.kubernetes.model.keycloakserver.KeycloakServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
//in execute component test
@Tag("in-process")
public class DeployDatabaseTest implements InProcessTestUtil, FluentTraversals {

    private static final String MY_KEYCLOAK_DB = MY_KEYCLOAK + "-db";
    private static final String MY_KEYCLOAK_DB_SERVICE = MY_KEYCLOAK_DB + "-service";
    private static final String MY_KEYCLOAK_DB_PVC = MY_KEYCLOAK_DB + "-pvc";
    private static final String MY_KEYCLOAK_DB_DEPLOYMENT = MY_KEYCLOAK_DB + "-deployment";
    private static final String MY_KEYCLOAK_DB_SECRET = MY_KEYCLOAK_DB + "-secret";
    private static final String MY_KEYCLOAK_DB_CONTAINER = MY_KEYCLOAK_DB + "-container";
    private static final String MY_KEYCLOAK_DB_ADMIN_SECRET = MY_KEYCLOAK_DB + "-admin-secret";
    private static final String DB_ADDR = "DB_ADDR";
    private static final String DB_PORT_VAR = "DB_PORT";
    private static final String DB_DATABASE = "DB_DATABASE";
    private static final String DB_USER = "DB_USER";
    private static final String DB_PASSWORD = "DB_PASSWORD";
    private static final String MY_KEYCLOAK_DATABASE = "my_keycloak_db";
    private static final String AUTH = "/auth";
    private final KeycloakServer keycloakServer = newKeycloakServer();
    @Spy
    private final SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble();
    @Mock
    private SimpleKeycloakClient keycloakClient;
    @InjectMocks
    private TestServerController testServerController = new TestServerController(client, keycloakClient);

    @Test
    public void testSecrets() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        //Given I have an KeycloakServer custom resource with MySQL as database
        final KeycloakServer newKeycloakServer = keycloakServer;
        client.entandoResources().putEntandoCustomResource(keycloakServer);
        // When I  deploy the KeycloakServer
        testServerController
                .onKeycloakServerAddition(newKeycloakServer.getMetadata().getNamespace(), newKeycloakServer.getMetadata().getName());

        //Then a K8S Secret was created with a name that reflects the KeycloakServer and the fact that it is an admin secret
        NamedArgumentCaptor<Secret> adminSecretCaptor = forResourceNamed(Secret.class, MY_KEYCLOAK_DB_ADMIN_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newKeycloakServer), adminSecretCaptor.capture());
        Secret theDbAdminSecret = adminSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(theDbAdminSecret), is("root"));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(theDbAdminSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(theDbAdminSecret), is(MY_KEYCLOAK));

        //And a K8S Secret was created with a name that reflects the KeycloakServer and the fact that it is the keycloakd db secret
        NamedArgumentCaptor<Secret> keycloakDbSecretCaptor = forResourceNamed(Secret.class, MY_KEYCLOAK_DB_SECRET);
        verify(client.secrets()).createSecretIfAbsent(eq(newKeycloakServer), keycloakDbSecretCaptor.capture());
        Secret keycloakDbSecret = keycloakDbSecretCaptor.getValue();
        assertThat(theKey(KubeUtils.USERNAME_KEY).on(keycloakDbSecret), is(MY_KEYCLOAK_DATABASE));
        assertThat(theKey(KubeUtils.PASSSWORD_KEY).on(keycloakDbSecret), is(not(emptyOrNullString())));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(keycloakDbSecret), is(MY_KEYCLOAK));

    }

    @Test
    public void testService() {
        //Given I have an KeycloakServer custom resource with MySQL as database
        KeycloakServer newKeycloakServer = keycloakServer;
        client.entandoResources().putEntandoCustomResource(keycloakServer);
        //And that K8S is up and receiving Service requests
        ServiceStatus dbServiceStatus = new ServiceStatus();
        lenient().when(client.services().loadService(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_SERVICE)))
                .then(respondWithServiceStatus(dbServiceStatus));

        //When the the KeycloakServerController is notified that a new KeycloakServer has been added
        testServerController
                .onKeycloakServerAddition(newKeycloakServer.getMetadata().getNamespace(), newKeycloakServer.getMetadata().getName());
        //Then a K8S Service was created with a name that reflects the EntandoApp and the fact that it is a JEE service
        NamedArgumentCaptor<Service> dbServiceCaptor = forResourceNamed(Service.class, MY_KEYCLOAK_DB_SERVICE);
        verify(client.services()).createService(eq(newKeycloakServer), dbServiceCaptor.capture());
        //And a selector that matches the Keyclaok DB pod
        Service dbService = dbServiceCaptor.getValue();
        Map<String, String> dbSelector = dbService.getSpec().getSelector();
        assertThat(dbSelector.get(DEPLOYMENT_LABEL_NAME), is(MY_KEYCLOAK_DB));
        assertThat(dbSelector.get(KEYCLOAK_SERVER_LABEL_NAME), is(MY_KEYCLOAK));
        //And the TCP port 3306 named 'db-port'
        assertThat(thePortNamed(DB_PORT).on(dbService).getPort(), is(3306));
        assertThat(thePortNamed(DB_PORT).on(dbService).getProtocol(), is(TCP));
        //And the state of the two services was reloaded from K8S
        verify(client.services()).loadService(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_SERVICE));
        //And K8S was instructed to update the status of the EntandoApp with the status of the java service
        //And the db service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesServiceStatus(dbServiceStatus)));
    }

    @Test
    public void testMysqlDeployment() {
        //Given I have an KeycloakServer custom resource with MySQL as database
        KeycloakServer newKeycloakServer = keycloakServer;
        client.entandoResources().putEntandoCustomResource(keycloakServer);
        //And K8S is receiving Deployment requests
        DeploymentStatus dbDeploymentStatus = new DeploymentStatus();
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(dbDeploymentStatus));
        //When the the KeycloakServerController is notified that a new KeycloakServer has been added
        testServerController
                .onKeycloakServerAddition(newKeycloakServer.getMetadata().getNamespace(), newKeycloakServer.getMetadata().getName());

        //Then two K8S deployments are created with a name that reflects the KeycloakServer name the
        NamedArgumentCaptor<Deployment> dbDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_KEYCLOAK_DB_DEPLOYMENT);
        verify(client.deployments()).createDeployment(eq(newKeycloakServer), dbDeploymentCaptor.capture());
        Deployment dbDeployment = dbDeploymentCaptor.getValue();
        Container theDbContainer = theContainerNamed(MY_KEYCLOAK_DB_CONTAINER).on(dbDeployment);
        //Exposing a port 3306
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getContainerPort(), is(3306));
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom registry and Entando image version specified
        assertThat(theDbContainer.getImage(), is("docker.io/centos/mysql-57-centos7:latest"));
        //With a Pod Template that has labels linking it to the previously created K8S Database Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_KEYCLOAK_DB));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_KEYCLOAK));

        //And the Deployment state was reloaded from K8S for both deployments
        verify(client.deployments()).loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_DEPLOYMENT));
        //And K8S was instructed to update the status of the EntandoApp with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesDeploymentStatus(dbDeploymentStatus)));
        //And all volumes have been mapped
        verifyThatAllVolumesAreMapped(newKeycloakServer, client, dbDeployment);
    }

    @Test
    public void testPostgresqlDeployment() {
        //Given I have an KeycloakServer custom resource with MySQL as database
        KeycloakServer newKeycloakServer = new DoneableKeycloakServer(keycloakServer, s -> s)
                .editSpec()
                .withDbms(DbmsImageVendor.POSTGRESQL)
                .endSpec()
                .done();
        client.entandoResources().putEntandoCustomResource(newKeycloakServer);
        //And K8S is receiving Deployment requests
        DeploymentStatus dbDeploymentStatus = new DeploymentStatus();
        //And K8S is receiving Deployment requests
        lenient().when(client.deployments().loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(dbDeploymentStatus));
        //When the the KeycloakServerController is notified that a new KeycloakServer has been added
        testServerController
                .onKeycloakServerAddition(newKeycloakServer.getMetadata().getNamespace(), newKeycloakServer.getMetadata().getName());

        //Then two K8S deployments are created with a name that reflects the KeycloakServer name the
        NamedArgumentCaptor<Deployment> dbDeploymentCaptor = forResourceNamed(Deployment.class,
                MY_KEYCLOAK_DB_DEPLOYMENT);
        verify(client.deployments()).createDeployment(eq(newKeycloakServer), dbDeploymentCaptor.capture());
        Deployment dbDeployment = dbDeploymentCaptor.getValue();
        Container theDbContainer = theContainerNamed(MY_KEYCLOAK_DB_CONTAINER).on(dbDeployment);
        //Exposing a port 5432
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getContainerPort(), is(5432));
        assertThat(thePortNamed(DB_PORT).on(theDbContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the custom registry and Entando image version specified
        assertThat(theDbContainer.getImage(), is("docker.io/centos/postgresql-96-centos7:latest"));
        //With a Pod Template that has labels linking it to the previously created K8S Database Service
        assertThat(theLabel(DEPLOYMENT_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_KEYCLOAK_DB));
        assertThat(theLabel(KEYCLOAK_SERVER_LABEL_NAME).on(dbDeployment.getSpec().getTemplate()), is(MY_KEYCLOAK));

        //And the Deployment state was reloaded from K8S for both deployments
        verify(client.deployments()).loadDeployment(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_DEPLOYMENT));
        //And K8S was instructed to update the status of the EntandoApp with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(matchesDeploymentStatus(dbDeploymentStatus)));
        //And all volumes have been mapped
        verifyThatAllVolumesAreMapped(newKeycloakServer, client, dbDeployment);
    }

    @Test
    public void testPersistentVolumeClaims() {
        //Given I have  a Keycloak server
        KeycloakServer newKeycloakServer = this.keycloakServer;
        client.entandoResources().putEntandoCustomResource(newKeycloakServer);
        //And that K8S is up and receiving PVC requests
        PersistentVolumeClaimStatus dbPvcStatus = new PersistentVolumeClaimStatus();
        lenient().when(client.persistentVolumeClaims()
                .loadPersistentVolumeClaim(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_PVC)))
                .then(respondWithPersistentVolumeClaimStatus(dbPvcStatus));

        //When the KeycloakController is notified that a new KeycloakServer has been added
        testServerController
                .onKeycloakServerAddition(newKeycloakServer.getMetadata().getNamespace(), newKeycloakServer.getMetadata().getName());

        //Then K8S was instructed to create a PersistentVolumeClaim for the DB and the JEE Server
        NamedArgumentCaptor<PersistentVolumeClaim> dbPvcCaptor = forResourceNamed(PersistentVolumeClaim.class,
                MY_KEYCLOAK_DB_PVC);
        verify(this.client.persistentVolumeClaims())
                .createPersistentVolumeClaim(eq(newKeycloakServer), dbPvcCaptor.capture());
        //With names that reflect the EntandoPlugin and the type of deployment the claim is used for
        PersistentVolumeClaim dbPvc = dbPvcCaptor.getValue();

        //And labels that link this PVC to the EntandoApp, the EntandoPlugin and the specific deployment
        assertThat(dbPvc.getMetadata().getLabels().get(KEYCLOAK_SERVER_LABEL_NAME), is(MY_KEYCLOAK));
        assertThat(dbPvc.getMetadata().getLabels().get(DEPLOYMENT_LABEL_NAME), is(MY_KEYCLOAK_DB));

        //And both PersistentVolumeClaims were reloaded from  K8S for its latest state
        verify(this.client.persistentVolumeClaims())
                .loadPersistentVolumeClaim(eq(newKeycloakServer), eq(MY_KEYCLOAK_DB_PVC));

        // And K8S was instructed to update the status of the EntandoPlugin with
        // the status of both PersistentVolumeClaims
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newKeycloakServer), argThat(containsThePersistentVolumeClaimStatus(dbPvcStatus)));
    }

}
