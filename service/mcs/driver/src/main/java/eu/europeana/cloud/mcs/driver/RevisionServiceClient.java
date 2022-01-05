package eu.europeana.cloud.mcs.driver;

import eu.europeana.cloud.common.model.Revision;
import eu.europeana.cloud.common.utils.Tags;
import eu.europeana.cloud.mcs.driver.exception.DriverException;
import eu.europeana.cloud.service.commons.utils.DateHelper;
import eu.europeana.cloud.service.mcs.exception.MCSException;
import eu.europeana.cloud.service.mcs.exception.RepresentationNotExistsException;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Set;

import static eu.europeana.cloud.common.web.ParamConstants.*;
import static eu.europeana.cloud.service.mcs.RestInterfaceConstants.*;

/**
 * Created by Tarek on 8/2/2016.
 */
public class RevisionServiceClient extends MCSClient {

    /**
     * Constructs a RevisionServiceClient
     *
     * @param baseUrl url of the MCS Rest Service
     */

    public RevisionServiceClient(String baseUrl) {
        this(baseUrl, DEFAULT_CONNECT_TIMEOUT_IN_MILLIS, DEFAULT_READ_TIMEOUT_IN_MILLIS);
    }

    public RevisionServiceClient(String baseUrl, final int connectTimeoutInMillis, final int readTimeoutInMillis) {
        super(baseUrl);

        this.client.property(ClientProperties.CONNECT_TIMEOUT, connectTimeoutInMillis);
        this.client.property(ClientProperties.READ_TIMEOUT, readTimeoutInMillis);
    }

    /**
     * Creates instance of RevisionServiceClient. Same as {@link #RevisionServiceClient(String)}
     * but includes username and password to perform authenticated requests.
     *
     * @param baseUrl URL of the MCS Rest Service
     */

    public RevisionServiceClient(String baseUrl, final String username, final String password) {
        this(baseUrl, username, password, DEFAULT_CONNECT_TIMEOUT_IN_MILLIS, DEFAULT_READ_TIMEOUT_IN_MILLIS);
    }


    public RevisionServiceClient(String baseUrl, final String username, final String password, final int connectTimeoutInMillis, final int readTimeoutInMillis) {
        super(baseUrl);

        client.register(HttpAuthenticationFeature.basicBuilder().credentials(username, password).build());
        client.property(ClientProperties.CONNECT_TIMEOUT, connectTimeoutInMillis);
        client.property(ClientProperties.READ_TIMEOUT, readTimeoutInMillis);
    }

    /**
     * add a revision
     *
     * @param cloudId            id of uploaded revision.
     * @param representationName representation name of uploaded revision.
     * @param version            a specific version of the representation.
     * @param revisionName       the name of revision
     * @param revisionProviderId revision provider id
     * @param tag                flag tag
     * @return URI to specific revision with specific tag inside a version.
     * @throws DriverException call to service has not succeeded because of server side error.
     * @throws MCSException    on unexpected situations.
     */
    public URI addRevision(String cloudId, String representationName, String version, String revisionName,
                           String revisionProviderId, String tag) throws MCSException {
        return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                () -> client
                        .target(baseUrl)
                        .path(REVISION_ADD_WITH_PROVIDER_TAG)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .resolveTemplate(REVISION_NAME, revisionName)
                        .resolveTemplate(REVISION_PROVIDER_ID, revisionProviderId)
                        .resolveTemplate(TAG, tag)
                        .request()
                        .post(null)
        );
    }

    /**
     * add a revision
     *
     * @param cloudId            id of uploaded revision.
     * @param representationName representation name of uploaded revision.
     * @param version            a specific version of the representation.
     * @param revision           revision
     * @return URI to revisions inside a version.
     * @throws RepresentationNotExistsException when representation does not exist in specified version.
     * @throws DriverException                  call to service has not succeeded because of server side error.
     * @throws MCSException                     on unexpected situations.
     */
    public URI addRevision(String cloudId, String representationName, String version, Revision revision) throws MCSException {
        return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                () -> client
                        .target(baseUrl)
                        .path(REVISION_ADD)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .accept(MediaType.APPLICATION_JSON).post(Entity.json(revision))
        );
    }

    /**
     * add a revision
     *
     * @param cloudId            id of uploaded revision.
     * @param representationName representation name of uploaded revision.
     * @param version            a specific version of the representation.
     * @param revision           revision
     * @param key                key of header request
     * @param value              value of header request
     * @return URI to revisions inside a version.
     * @throws RepresentationNotExistsException when representation does not exist in specified version.
     * @throws DriverException                  call to service has not succeeded because of server side error.
     * @throws MCSException                     on unexpected situations.
     */
    public URI addRevision(String cloudId, String representationName, String version, Revision revision, String key, String value) throws MCSException {
        return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                () -> client
                        .target(baseUrl)
                        .path(REVISION_ADD)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .header(key, value)
                        .accept(MediaType.APPLICATION_JSON).post(Entity.json(revision))
        );
    }

    /**
     * add a revision
     *
     * @param cloudId            cloud id of the record (required).
     * @param representationName schema of representation (required).
     * @param version            a specific version of the representation(required).
     * @param revisionName       the name of revision (required).
     * @param revisionProviderId revision provider id (required).
     * @param tags               set of tags (acceptance,published,deleted)
     * @return URI to a revision tags inside a version.
     * @throws RepresentationNotExistsException when representation does not exist in specified version.
     * @throws DriverException                  call to service has not succeeded because of server side error.
     * @throws MCSException                     on unexpected situations.
     */
    public URI addRevision(String cloudId, String representationName, String version,
                           String revisionName, String revisionProviderId, Set<Tags> tags) throws MCSException {

        Form form = new Form();
        for (Tags tag : tags) {
            form.param(F_TAGS, tag.getTag());
        }

        return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                () -> client
                        .target(baseUrl)
                        .path(REVISION_ADD_WITH_PROVIDER)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .resolveTemplate(REVISION_NAME, revisionName)
                        .resolveTemplate(REVISION_PROVIDER_ID, revisionProviderId)
                        .request()
                        .post(Entity.form(form))
        );
    }

    /**
     * Remove a revision
     *
     * @param cloudId            cloud Id
     * @param representationName representation name
     * @param version            representation version
     * @param revision           the revision to delete
     * @throws RepresentationNotExistsException throws if given representation not exists
     */
    public void deleteRevision(String cloudId, String representationName, String version, Revision revision) throws MCSException {
        manageResponse(new ResponseParams<>(Void.class, Response.Status.NO_CONTENT),
                () -> client
                        .target(baseUrl)
                        .path(REVISION_DELETE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .resolveTemplate(REVISION_NAME, revision.getRevisionName())
                        .resolveTemplate(REVISION_PROVIDER_ID, revision.getRevisionProviderId())
                        .queryParam(F_REVISION_TIMESTAMP, DateHelper.getISODateString(revision.getCreationTimeStamp()))
                        .request()
                        .delete()
        );
    }

    /**
     * Remove a revision
     *
     * @param cloudId            cloud Id
     * @param representationName representation name
     * @param version            representation version
     * @param revision           the revision
     * @param key                authorisation key
     * @param value              authorisation value
     * @throws RepresentationNotExistsException throws if given representation not exists
     */
    public void deleteRevision(String cloudId, String representationName, String version, Revision revision, String key, String value) throws MCSException {
        manageResponse(new ResponseParams<>(Void.class, Response.Status.NO_CONTENT),
                () -> client.target(baseUrl)
                        .path(REVISION_DELETE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .resolveTemplate(REVISION_NAME, revision.getRevisionName())
                        .resolveTemplate(REVISION_PROVIDER_ID, revision.getRevisionProviderId())
                        .queryParam(F_REVISION_TIMESTAMP, DateHelper.getISODateString(revision.getCreationTimeStamp()))
                        .request()
                        .header(key, value)
                        .delete()
        );
    }
}
