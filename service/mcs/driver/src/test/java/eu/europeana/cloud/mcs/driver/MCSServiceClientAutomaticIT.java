package eu.europeana.cloud.mcs.driver;

import eu.europeana.cloud.client.uis.rest.CloudException;
import eu.europeana.cloud.client.uis.rest.UISClient;
import eu.europeana.cloud.common.model.Permission;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.service.mcs.exception.AccessDeniedOrObjectDoesNotExistException;
import eu.europeana.cloud.service.mcs.exception.MCSException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static eu.europeana.cloud.mcs.driver.Config.*;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MCSServiceClientAutomaticIT {
    private static final String LOCAL_TEST_URL = MCS_URL;

    private static final String USER_NAME = Config.ECLOUD_USER;
    private static final String USER_PASSWORD = Config.ECLOUD_PASSWORD;

    private static final Logger LOGGER = LoggerFactory.getLogger(MCSServiceClientAutomaticIT.class);
    public static final String DATASET_ID = "e2e_api_tests";
    private static final String DATASET_DESCRIPTION = "Dataset for automatic tests";
    public static final String PROVIDER = "xxx";
    private static final String RECORD_1 = "/99991/recrods1";
    private static final String RECORD_2 = "/99991/recrods2";
    private static final String REPRESENTATION_NAME = "xxx";
    private static final String FILE_NAME = "test.txt";
    private static final String FILE_MEDIA_TYPE = MediaType.TEXT_PLAIN;

    private UUID VERSION = UUID.fromString(new com.eaio.uuid.UUID().toString());
    private DataSetServiceClient dataSetServiceClient;

    private RecordServiceClient recordServiceClient;
    private UISClient uisClient;
    private String cloudId1;
    private String cloudId2;
    private final InputStream fileData = new ByteArrayInputStream("It is test uploaded file content."
            .getBytes(StandardCharsets.UTF_8));

    //TODO uzupelnić inne metody
    @Before
    public void setup() throws MCSException, CloudException {
        uisClient = new UISClient(UIS_URL, USER_NAME, USER_PASSWORD);
        dataSetServiceClient = new DataSetServiceClient(LOCAL_TEST_URL, USER_NAME, USER_PASSWORD);
        recordServiceClient = new RecordServiceClient(LOCAL_TEST_URL, USER_NAME, USER_PASSWORD);
        clearDateFromPreviousTests();

        cloudId1 = uisClient.createCloudId(PROVIDER, RECORD_1).getId();
        cloudId2 = uisClient.createCloudId(PROVIDER, RECORD_1).getId();
        dataSetServiceClient.createDataSet(PROVIDER, DATASET_ID, DATASET_DESCRIPTION);
    }

    private void clearDateFromPreviousTests() throws MCSException {
        if (dataSetServiceClient.datasetExists(PROVIDER, DATASET_ID)) {
            RepresentationIterator it = dataSetServiceClient.getRepresentationIterator(PROVIDER, DATASET_ID);
            while (it.hasNext()) {
                Representation r = it.next();
                recordServiceClient.deleteRepresentation(r.getCloudId(), r.getRepresentationName(), r.getVersion());
            }
            dataSetServiceClient.removeDataSetPermissionsForUser(PROVIDER, DATASET_ID, Permission.ALL, OTHER_USER);
            dataSetServiceClient.deleteDataSet(PROVIDER, DATASET_ID);
        }
    }

    @After
    public void close() {
        dataSetServiceClient.close();
    }

    @Test
    public void shouldCreateEmptyRepresentation() throws MCSException {
        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, DATASET_ID);

        assertEquals(1, recordServiceClient.getRepresentations(cloudId1, REPRESENTATION_NAME).size());
    }


    @Test(expected = AccessDeniedOrObjectDoesNotExistException.class)
    public void shouldNotCreateEmptyRepresentationIfUserHasNotRightsToDataset() throws MCSException {
        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, DATASET_ID,
                AUTHORIZATION, OTHER_USER_AUTHORIZATION_HEADER);
    }

    @Test
    public void shouldCreateEmptyRepresentationForGivenVersion() throws MCSException {
        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, VERSION, DATASET_ID,
                AUTHORIZATION, ECLOUD_AUTHORIZATION_HEADER);

        List<Representation> createdRepresentations = recordServiceClient.getRepresentations(cloudId1, REPRESENTATION_NAME);
        assertEquals(1, createdRepresentations.size());
        assertEquals(VERSION.toString(), createdRepresentations.get(0).getVersion());

    }

    @Test
    public void shouldCreateEmptyRepresentationWithFile() throws MCSException, IOException {
        System.out.println(recordServiceClient);
        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, DATASET_ID, fileData, FILE_MEDIA_TYPE);

        List<Representation> createdRepresentations = recordServiceClient.getRepresentations(cloudId1, REPRESENTATION_NAME);
        assertEquals(1, createdRepresentations.size());
        assertEquals(1, createdRepresentations.get(0).getFiles().size());
    }


    @Test(expected = AccessDeniedOrObjectDoesNotExistException.class)
    public void shouldNotCreateEmptyRepresentationWithFileIfUserHasNotRightsToDataset() throws MCSException, IOException {
        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, DATASET_ID, fileData, FILE_NAME, FILE_MEDIA_TYPE,
                AUTHORIZATION, OTHER_USER_AUTHORIZATION_HEADER);
    }

    @Test
    public void shouldCreateEmptyRepresentationWithNamedFile() throws MCSException, IOException {
        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, DATASET_ID, fileData, FILE_NAME, FILE_MEDIA_TYPE,
                AUTHORIZATION, ECLOUD_AUTHORIZATION_HEADER);

        List<Representation> createdRepresentations = recordServiceClient.getRepresentations(cloudId1, REPRESENTATION_NAME);
        assertEquals(1, createdRepresentations.size());
        assertEquals(1, createdRepresentations.get(0).getFiles().size());
    }

    @Test
    public void shouldCreateEmptyRepresentationWithNamedFileForGivenVersion() throws MCSException, IOException {
        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, VERSION, DATASET_ID,
                fileData, FILE_NAME, FILE_MEDIA_TYPE, AUTHORIZATION, ECLOUD_AUTHORIZATION_HEADER);

        List<Representation> createdRepresentations = recordServiceClient.getRepresentations(cloudId1, REPRESENTATION_NAME);
        assertEquals(1, createdRepresentations.size());
        assertEquals(VERSION.toString(), createdRepresentations.get(0).getVersion());
        assertEquals(1, createdRepresentations.get(0).getFiles().size());

    }

    @Test
    public void shouldProperlyChangeDataSetPermissionsForUser() throws MCSException {
        dataSetServiceClient.updateDataSetPermissionsForUser(PROVIDER, DATASET_ID, Permission.WRITE, OTHER_USER);

        recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, DATASET_ID,
                AUTHORIZATION, OTHER_USER_AUTHORIZATION_HEADER);

        assertEquals(1, recordServiceClient.getRepresentations(cloudId1, REPRESENTATION_NAME).size());

        dataSetServiceClient.removeDataSetPermissionsForUser(PROVIDER, DATASET_ID, Permission.WRITE, OTHER_USER);

        try {
            recordServiceClient.createRepresentation(cloudId1, REPRESENTATION_NAME, PROVIDER, DATASET_ID,
                    AUTHORIZATION, OTHER_USER_AUTHORIZATION_HEADER);
            fail("Should not create revision!");
        } catch (AccessDeniedOrObjectDoesNotExistException e) {
            //OK
        }
    }

    public void shouldProperlyGiveReadAccessToEveryone() {
        //TODO Repair method when the resource will be repaired, and client updated
    }


}
