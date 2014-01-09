package eu.europeana.cloud.service.mcs.persistent.uis;

import eu.europeana.cloud.service.mcs.persistent.uis.UISClientHandlerImpl;
import eu.europeana.cloud.client.uis.rest.CloudException;
import eu.europeana.cloud.client.uis.rest.UISClient;
import eu.europeana.cloud.common.exceptions.GenericException;
import eu.europeana.cloud.common.exceptions.ProviderDoesNotExistException;
import eu.europeana.cloud.common.model.CloudId;
import eu.europeana.cloud.common.model.IdentifierErrorInfo;
import eu.europeana.cloud.common.response.ErrorInfo;
import eu.europeana.cloud.service.mcs.persistent.exception.SystemException;
import eu.europeana.cloud.service.uis.exception.CloudIdDoesNotExistException;
import eu.europeana.cloud.service.uis.status.IdentifierErrorTemplate;

import java.util.ArrayList;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(value = { "classpath:/uisIntegrationTestContext.xml" })
public class UISHandlerTest {

    @Autowired
    private UISClientHandlerImpl handler;

    @Autowired
    private UISClient uisClient;


    @After
    public void cleanUp() {
        Mockito.reset(uisClient);
    }


    @Test(expected = SystemException.class)
    public void shouldThrowExWhenRecordWhenUISFailure()
            throws Exception {
        String cloudId = "cloudId";
		Mockito.when(uisClient.getRecordId(cloudId)).thenThrow(
				new CloudException(cloudId, new GenericException(new IdentifierErrorInfo(
						IdentifierErrorTemplate.GENERIC_ERROR.getHttpCode(), IdentifierErrorTemplate.GENERIC_ERROR
								.getErrorInfo("")))));
		handler.recordExistInUIS(cloudId);
	}


    @Test
    public void shouldFailIfRecordNotFoundInUIS()
            throws Exception {
        String cloudId = "cloudId";
		Mockito.when(uisClient.getRecordId(cloudId)).thenThrow(
				new CloudException(cloudId, new CloudIdDoesNotExistException(new ErrorInfo("",""))));
		assertFalse(handler.recordExistInUIS(cloudId));
	}


    @Test(expected = IllegalStateException.class)
    public void shouldThrowExWhenGotNullFromUIS()
            throws Exception {
        String cloudId = "cloudId";
        Mockito.when(uisClient.getRecordId(cloudId)).thenReturn(null);
        handler.recordExistInUIS(cloudId);
    }


    @Test(expected = IllegalStateException.class)
    public void shouldThrowExWhenGotEmptyListFromUIS()
            throws Exception {
        String cloudId = "cloudId";
        Mockito.when(uisClient.getRecordId(cloudId)).thenReturn(new ArrayList<CloudId>());
        handler.recordExistInUIS(cloudId);
    }


    @Test(expected = IllegalStateException.class)
    public void shouldThrowExWhenCloudIdNotOnListFromUIS()
            throws Exception {
        String cloudId = "cloudId";
        CloudId cl = new CloudId();
        cl.setId("66666");
        ArrayList<CloudId> result = new ArrayList<>();
        result.add(cl);

        Mockito.when(uisClient.getRecordId(cloudId)).thenReturn(result);

        handler.recordExistInUIS(cloudId);
    }


    @Test
    public void shouldReturnTrueWhenRecordExistsInUIS()
            throws Exception {
        String cloudId = "cloudId";
        CloudId cl = new CloudId();
        cl.setId(cloudId);
        ArrayList<CloudId> result = new ArrayList<>();
        result.add(cl);

        Mockito.when(uisClient.getRecordId(cloudId)).thenReturn(result);

        assertTrue(handler.recordExistInUIS(cloudId));
    }

}