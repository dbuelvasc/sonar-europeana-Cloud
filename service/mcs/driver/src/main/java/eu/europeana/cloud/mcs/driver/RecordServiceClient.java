package eu.europeana.cloud.mcs.driver;

import eu.europeana.cloud.common.filter.ECloudBasicAuthFilter;
import eu.europeana.cloud.common.model.Permission;
import eu.europeana.cloud.common.model.Record;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.common.model.Revision;
import eu.europeana.cloud.common.web.ParamConstants;
import eu.europeana.cloud.service.commons.utils.DateHelper;
import eu.europeana.cloud.service.mcs.exception.*;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static eu.europeana.cloud.common.web.ParamConstants.*;
import static eu.europeana.cloud.service.mcs.RestInterfaceConstants.*;

/**
 * Exposes API related for records.
 */
public class RecordServiceClient extends MCSClient {

    /**
     * Creates instance of RecordServiceClient.
     *
     * @param baseUrl URL of the MCS Rest Service
     */
    public RecordServiceClient(String baseUrl) {
        this(baseUrl, DEFAULT_CONNECT_TIMEOUT_IN_MILLIS, DEFAULT_READ_TIMEOUT_IN_MILLIS);
    }

    public RecordServiceClient(String baseUrl, final int connectTimeoutInMillis, final int readTimeoutInMillis) {
        this(baseUrl, null, null, null, connectTimeoutInMillis, readTimeoutInMillis);
    }

    /**
     * Creates instance of RecordServiceClient. Same as {@link #RecordServiceClient(String)}
     * but includes username and password to perform authenticated requests.
     *
     * @param baseUrl URL of the MCS Rest Service
     */
    public RecordServiceClient(String baseUrl, final String username, final String password) {
        this(baseUrl, null, username, password, DEFAULT_CONNECT_TIMEOUT_IN_MILLIS, DEFAULT_READ_TIMEOUT_IN_MILLIS);
    }

    /**
     * Constructor with url and http header authorisation
     *
     * @param baseUrl             URL to connect to
     * @param authorizationHeader Authorization header
     */
    public RecordServiceClient(String baseUrl, final String authorizationHeader) {
        this(baseUrl, authorizationHeader, null, null, DEFAULT_CONNECT_TIMEOUT_IN_MILLIS, DEFAULT_READ_TIMEOUT_IN_MILLIS);
    }


    /**
     * All parameters' constructor used by another one
     *
     * @param baseUrl                URL of the MCS Rest Service
     * @param authorizationHeader    Authorization header - used instead username/password pair
     * @param username               Username to HTTP authorisation  (use together with password)
     * @param password               Password to HTTP authorisation (use together with username)
     * @param connectTimeoutInMillis Timeout for waiting for connecting
     * @param readTimeoutInMillis    Timeout for getting data
     */
    public RecordServiceClient(String baseUrl, final String authorizationHeader, final String username, final String password,
                               final int connectTimeoutInMillis, final int readTimeoutInMillis) {
        super(baseUrl);

        if (authorizationHeader != null) {
            client.register(new ECloudBasicAuthFilter(authorizationHeader));
        } else if (username != null || password != null) {
            client.register(HttpAuthenticationFeature.basicBuilder().credentials(username, password).build());
        }

        this.client.property(ClientProperties.CONNECT_TIMEOUT, connectTimeoutInMillis);
        this.client.property(ClientProperties.READ_TIMEOUT, readTimeoutInMillis);
    }

    /**
     * Returns record with all its latest persistent representations.
     *
     * @param cloudId id of the record (required)
     * @return record of specified cloudId (required)
     * @throws RecordNotExistsException when id is not known UIS Service
     * @throws MCSException             on unexpected situations
     */
    public Record getRecord(String cloudId) throws MCSException {
        return manageResponse(new ResponseParams<>(Record.class),
                () -> client
                        .target(baseUrl)
                        .path(RECORDS_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .request()
                        .get()
        );
    }

    /**
     * Deletes record with all its representations in all versions.
     * <p/>
     * Does not remove mapping from Unique Identifier Service. If record exists,
     * but nothing was deleted (it had no representations assigned), nothing
     * happens.
     *
     * @param cloudId id of deleted record (required)
     * @throws RecordNotExistsException if cloudId is not known UIS Service
     * @throws MCSException             on unexpected situations
     */
    public void deleteRecord(String cloudId) throws MCSException {
        manageResponse(new ResponseParams<>(Void.class, Response.Status.NO_CONTENT),
                () -> client
                        .target(baseUrl)
                        .path(RECORDS_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .request()
                        .delete()
        );
    }

    /**
     * Lists all latest persistent versions of record representation.
     *
     * @param cloudId id of record from which to get representations (required)
     * @return list of representations
     * @throws RecordNotExistsException if cloudId is not known UIS Service
     * @throws MCSException             on unexpected situations
     */
    public List<Representation> getRepresentations(String cloudId) throws MCSException {
        return manageResponse(new ResponseParams<>(new GenericType<List<Representation>>() {
                }),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATIONS_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .request()
                        .get()
        );
    }

    /**
     * Returns latest persistent version of representation.
     *
     * @param cloudId            id of record from which to get representations (required)
     * @param representationName name of the representation (required)
     * @return representation of specified representationName and cloudId
     * @throws RepresentationNotExistsException representation does not exist or
     *                                          no persistent version of this representation exists
     * @throws MCSException                     on unexpected situations
     */
    public Representation getRepresentation(String cloudId, String representationName) throws MCSException {
        return manageResponse(new ResponseParams<>(Representation.class, new Response.Status[]{Response.Status.OK, Response.Status.TEMPORARY_REDIRECT}),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .request()
                        .get()
        );
    }

    public URI createRepresentation(String cloudId, String representationName, String providerId, String key, String value) throws MCSException {
        return createRepresentation(cloudId, representationName, providerId, (UUID) null, key, value);
    }

    /**
     * Creates new representation version.
     *
     * @param cloudId            id of the record in which to create the representation
     *                           (required)
     * @param representationName name of the representation to be created
     *                           (required)
     * @param providerId         provider of this representation version (required)
     * @param version            representation's version
     * @return URI to the created representation
     * @throws ProviderNotExistsException when no provider with given id exists
     * @throws RecordNotExistsException   when cloud id is not known to UIS
     *                                    Service
     * @throws MCSException               on unexpected situations
     */
    public URI createRepresentation(String cloudId, String representationName, String providerId, UUID version, String key, String value) throws MCSException {
        var form = new Form();
        form.param(PROVIDER_ID, providerId);
        if (version != null) {
            form.param(VERSION, version.toString());
        }

        if (key == null) {
            return createRepresentation(cloudId, representationName, form);
        } else {
            return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                    () -> client.target(baseUrl)
                            .path(REPRESENTATION_RESOURCE)
                            .resolveTemplate(CLOUD_ID, cloudId)
                            .resolveTemplate(REPRESENTATION_NAME, representationName)
                            .request()
                            .header(key, value)
                            .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            );
        }
    }

    private URI createRepresentation(String cloudId, String representationName, Form form) throws MCSException {
        return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                () -> client.target(baseUrl)
                        .path(REPRESENTATION_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .request()
                        .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE))
        );
    }


    public URI createRepresentation(String cloudId, String representationName, String providerId) throws MCSException {
        return createRepresentation(cloudId, representationName, providerId, (String) null, null);
    }

    /**
     * Creates new representation version, uploads a file and makes this representation persistent (in one request)
     *
     * @param cloudId            id of the record in which to create the representation
     *                           (required)
     * @param representationName name of the representation to be created
     *                           (required)
     * @param providerId         provider of this representation version (required)
     * @param data               file that should be uploaded (required)
     * @param fileName           name for created file
     * @param mediaType          mimeType of uploaded file
     * @return URI to created file
     */
    public URI createRepresentation(String cloudId,
                                    String representationName,
                                    String providerId,
                                    InputStream data,
                                    String fileName,
                                    String mediaType) throws IOException, MCSException {

        var multiPart = prepareRequestBody(providerId, data, fileName, mediaType);

        try {
            return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                    () -> client
                            .target(baseUrl)
                            .path(FILE_UPLOAD_RESOURCE)
                            .resolveTemplate(CLOUD_ID, cloudId)
                            .resolveTemplate(REPRESENTATION_NAME, representationName)
                            .request()
                            .header("Content-Type", "multipart/form-data")
                            .post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA)));
        } finally {
            closeDataSources(data, multiPart);
        }
    }

    public URI createRepresentation(String cloudId, String representationName, String providerId, InputStream data,
                                    String fileName, String mediaType, String key, String value) throws IOException, MCSException {
        return createRepresentation(cloudId, representationName, providerId, null, data, fileName, mediaType, key, value);
    }

    /**
     * Creates new representation version, uploads a file and makes this representation persistent (in one request)
     *
     * @param cloudId            id of the record in which to create the representation
     *                           (required)
     * @param representationName name of the representation to be created
     *                           (required)
     * @param providerId         provider of this representation version (required)
     * @param data               file that should be uploaded (required)
     * @param fileName           name for created file
     * @param mediaType          mimeType of uploaded file
     * @param key                key to header request
     * @param value              value to header request
     * @return URI to created file
     */
    public URI createRepresentation(String cloudId,
                                    String representationName,
                                    String providerId,
                                    UUID version,
                                    InputStream data,
                                    String fileName,
                                    String mediaType,
                                    String key, String value) throws IOException, MCSException {

        var multiPart = prepareRequestBody(providerId, data, fileName, mediaType);
        if (version != null) {
            multiPart.field(VERSION, version.toString());
        }

        try {
            return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                    () -> client
                            .target(baseUrl)
                            .path(FILE_UPLOAD_RESOURCE)
                            .resolveTemplate(CLOUD_ID, cloudId)
                            .resolveTemplate(REPRESENTATION_NAME, representationName)
                            .request()
                            .header(key, value)
                            .post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA))
            );
        } finally {
            closeDataSources(data, multiPart);
        }
    }


    /**
     * Creates new representation version, uploads a file and makes this representation persistent (in one request)
     *
     * @param cloudId            id of the record in which to create the representation
     *                           (required)
     * @param representationName name of the representation to be created
     *                           (required)
     * @param providerId         provider of this representation version (required)
     * @param data               file that should be uploaded (required)
     * @param mediaType          mimeType of uploaded file
     * @return URI to created file
     * @throws MCSException throws if something went wrong
     */
    public URI createRepresentation(String cloudId, String representationName, String providerId,
                                    InputStream data, String mediaType) throws IOException, MCSException {

        return this.createRepresentation(cloudId, representationName, providerId, data, null, mediaType);
    }

    /**
     * Deletes representation with all versions.
     *
     * @param cloudId            id of the record to delete representation from (required)
     * @param representationName representation name of deleted representation
     *                           (required)
     * @throws RepresentationNotExistsException if specified Representation does
     *                                          not exist
     * @throws MCSException                     on unexpected situations
     */
    public void deleteRepresentation(String cloudId, String representationName) throws MCSException {

        manageResponse(new ResponseParams<>(Void.class, Response.Status.NO_CONTENT),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .request()
                        .delete()
        );
    }

    /**
     * Lists all versions of record representation.
     *
     * @param cloudId            id of the record to get representation from (required)
     * @param representationName name of the representation (required)
     * @return representation versions list
     * @throws RepresentationNotExistsException if specified Representation does
     *                                          not exist
     * @throws MCSException                     on unexpected situations
     */
    public List<Representation> getRepresentations(String cloudId, String representationName) throws MCSException {

        return manageResponse(new ResponseParams<>(new GenericType<List<Representation>>() {
                }),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_VERSIONS_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .request()
                        .get()
        );
    }

    /**
     * Returns representation in specified version.
     * <p/>
     * If Version = LATEST, will redirect to actual latest persistent version at
     * the moment of invoking this method.
     *
     * @param cloudId            id of the record to get representation from (required)
     * @param representationName name of the representation (required)
     * @param version            version of the representation to be obtained;
     *                           if version is equal LATEST function will return the latest persistent version (required)
     * @return requested representation version
     * @throws RepresentationNotExistsException if specified representation does
     *                                          not exist
     * @throws MCSException                     on unexpected situations
     */
    public Representation getRepresentation(String cloudId, String representationName, String version) throws MCSException {
        return manageResponse(new ResponseParams<>(Representation.class),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_VERSION)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .get()
        );
    }

    /**
     * Returns representation in specified version.
     * <p/>
     * If Version = LATEST, will redirect to actual latest persistent version at
     * the moment of invoking this method.
     *
     * @param cloudId            id of the record to get representation from (required)
     * @param representationName name of the representation (required)
     * @param version            version of the representation to be obtained;
     *                           if version is equal LATEST function will return the latest persistent version (required)
     * @param key                key to header
     * @param value              value to header
     * @return requested representation version
     * @throws RepresentationNotExistsException if specified representation does
     *                                          not exist
     * @throws MCSException                     on unexpected situations
     */
    public Representation getRepresentation(String cloudId, String representationName, String version, String key, String value) throws MCSException {
        return manageResponse(new ResponseParams<>(Representation.class),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_VERSION)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .header(key, value)
                        .get()
        );
    }

    /**
     * Deletes representation in specified version.
     *
     * @param cloudId            id of the record to delete representation version from
     *                           (required)
     * @param representationName name of the representation (required)
     * @param version            the deleted version of the representation (required)
     * @throws RepresentationNotExistsException              if specified representation does
     *                                                       not exist
     * @throws CannotModifyPersistentRepresentationException if specified
     *                                                       representation is persistent and thus cannot be removed
     * @throws MCSException                                  on unexpected situations
     */
    public void deleteRepresentation(String cloudId, String representationName, String version) throws MCSException {
        manageResponse(new ResponseParams<>(Void.class, Response.Status.NO_CONTENT),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_VERSION)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .delete()
        );
    }

    public void deleteRepresentation(String cloudId, String representationName, String version, String key, String value) throws MCSException {
        manageResponse(new ResponseParams<>(Void.class, Response.Status.NO_CONTENT),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_VERSION)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .header(key, value)
                        .delete()
        );
    }

    /**
     * Copies all information from one representation version to another.
     * <p/>
     * Copies all information with all files and their content from one
     * representation version to a new temporary one.
     *
     * @param cloudId            id of the record that holds representation (required)
     * @param representationName name of the copied representation (required)
     * @param version            version of the copied representation (required)
     * @return URI to the created copy of representation
     * @throws RepresentationNotExistsException if specified representation
     *                                          version does not exist
     * @throws MCSException                     on unexpected situations
     */
    public URI copyRepresentation(String cloudId, String representationName, String version) throws MCSException {
        return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_VERSION_COPY)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .post(Entity.entity(new Form(), MediaType.APPLICATION_FORM_URLENCODED_TYPE))
        );
    }

    /**
     * Makes specified temporary representation version persistent.
     *
     * @param cloudId            id of the record that holds representation (required)
     * @param representationName name of the representation to be persisted
     *                           (required)
     * @param version            version that should be made persistent (required)
     * @return URI to the persisted representation
     * @throws RepresentationNotExistsException              when representation does not
     *                                                       exist in specified version
     * @throws CannotModifyPersistentRepresentationException when representation
     *                                                       version is already persistent
     * @throws CannotPersistEmptyRepresentationException     when representation
     *                                                       version has no file attached and thus cannot be made persistent
     * @throws MCSException                                  on unexpected situations
     */
    public URI persistRepresentation(String cloudId, String representationName, String version) throws MCSException {
        return manageResponse(new ResponseParams<>(URI.class, Response.Status.CREATED),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_VERSION_PERSIST)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .post(Entity.entity(new Form(), MediaType.APPLICATION_FORM_URLENCODED_TYPE))
        );
    }

    /**
     * Adds selected permission(s) to selected representation version.
     *
     * @param cloudId            record identifier
     * @param representationName representation name
     * @param version            representation version
     * @param userName           user who will get access to representation version
     * @param permission         permission that will be granted
     * @throws MCSException throws if something went wrong
     */
    public void grantPermissionsToVersion(String cloudId, String representationName, String version,
                                          String userName, Permission permission) throws MCSException {
        Response.Status status = manageResponse(new ResponseParams<>(Response.Status.class, new Response.Status[]{Response.Status.NOT_MODIFIED, Response.Status.OK}),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_PERMISSION)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .resolveTemplate(PERMISSION, permission.getValue())
                        .resolveTemplate(USER_NAME, userName)
                        .request()
                        .post(null)
        );

        if (status == Response.Status.NOT_MODIFIED) {
            throw new MCSException("Permissions not modified");
        }
    }

    /**
     * Revokes permission(s) to selected representation version.
     *
     * @param cloudId            record identifier
     * @param representationName representation name
     * @param version            representation version
     * @param userName           user who will get access to representation version
     * @param permission         permission that will be granted
     * @throws MCSException throws if something went wrong
     */
    public void revokePermissionsToVersion(String cloudId, String representationName, String version,
                                           String userName, Permission permission) throws MCSException {

        manageResponse(new ResponseParams<>(Void.class, Response.Status.NO_CONTENT),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_PERMISSION)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .resolveTemplate(PERMISSION, permission.getValue())
                        .resolveTemplate(USER_NAME, userName)
                        .request()
                        .delete());
    }

    /**
     * Adds selected permission(s) to selected representation version.
     *
     * @param cloudId            record identifier
     * @param representationName representation name
     * @param version            representation version
     * @throws MCSException throws if something went wrong
     */
    public void permitVersion(String cloudId, String representationName, String version) throws MCSException {
        manageResponse(new ResponseParams<>(Void.class),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_PERMIT)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(VERSION, version)
                        .request()
                        .post(null)
        );
    }

    /**
     * Returns representation in specified version.
     * <p/>
     * If Version = LATEST, will redirect to actual latest persistent version at
     * the moment of invoking this method.
     *
     * @param cloudId            id of the record to get representation from (required)
     * @param representationName name of the representation (required)
     * @param revisionName       revision name (required)
     * @param revisionProviderId revision provider identifier, together with revisionId it is used to determine the correct revision (required)
     * @return requested representation version
     * @throws RepresentationNotExistsException if specified representation does
     *                                          not exist
     * @throws RepresentationNotExistsException on representation does not exist
     * @throws MCSException                     on unexpected situations
     * @deprecated              since 6-SNAPSHOT. The method {@link #getRepresentationsByRevision(String, String, Revision)} should be used instead
     */
    @Deprecated(since = "6-SNAPSHOT")
    public List<Representation> getRepresentationsByRevision(String cloudId, String representationName, String revisionName,
                                                             String revisionProviderId, String revisionTimestamp) throws MCSException {
        return getRepresentationsByRevision(cloudId, representationName, new Revision(revisionName, revisionProviderId, DateHelper.parseISODate(revisionTimestamp)));
    }


    /**
     * Returns representation in specified version.
     * <p/>
     * If Version = LATEST, will redirect to actual latest persistent version at
     * the moment of invoking this method.
     *
     * @param cloudId            id of the record to get representation from (required)
     * @param representationName name of the representation (required)
     * @param revision           the revision (required) (revisionProviderId is required)
     * @return requested representation version
     * @throws RepresentationNotExistsException if specified representation does
     *                                          not exist
     * @throws RepresentationNotExistsException on representation does not exist
     * @throws MCSException                     on unexpected situations
     */
    public List<Representation> getRepresentationsByRevision(String cloudId, String representationName, Revision revision) throws MCSException {

        if(revision.getRevisionProviderId() == null) {
            throw new MCSException("RevisionProviderId is required");
        }

        return manageResponse(new ResponseParams<>(new GenericType<List<Representation>>() {}),
                () -> client
                        .target(baseUrl)
                        .path(REPRESENTATION_REVISIONS_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(REVISION_NAME, revision.getRevisionName())
                        .queryParam(F_REVISION_PROVIDER_ID, revision.getRevisionProviderId())
                        .queryParam(F_REVISION_TIMESTAMP, DateHelper.getISODateString(revision.getCreationTimeStamp()) )
                        .request()
                        .get()
        );
    }



    public List<Representation> getRepresentationsByRevision(
            String cloudId,
            String representationName,
            String revisionName,
            String revisionProviderId,
            String revisionTimestamp,
            String key,
            String value)
            throws MCSException {

        if (revisionProviderId == null) {
            throw new MCSException("RevisionProviderId is required");
        }

        return manageResponse(new ResponseParams<>(new GenericType<List<Representation>>() {}),
                () -> client.target(baseUrl)
                        .path(REPRESENTATION_REVISIONS_RESOURCE)
                        .resolveTemplate(CLOUD_ID, cloudId)
                        .resolveTemplate(REPRESENTATION_NAME, representationName)
                        .resolveTemplate(REVISION_NAME, revisionName)
                        .queryParam(F_REVISION_PROVIDER_ID, revisionProviderId)
                        .queryParam(F_REVISION_TIMESTAMP, revisionTimestamp)
                        .request()
                        .header(key, value)
                        .get()
        );
    }

    private FormDataMultiPart prepareRequestBody(String providerId, InputStream data, String fileName, String mediaType) {
        FormDataMultiPart requestBody = new FormDataMultiPart();
        requestBody
                .field(ParamConstants.F_PROVIDER, providerId)
                .field(ParamConstants.F_FILE_MIME, mediaType)
                .bodyPart(new StreamDataBodyPart(ParamConstants.F_FILE_DATA, data, MediaType.APPLICATION_OCTET_STREAM));

        if (fileName == null || fileName.trim().isEmpty()) {
            fileName = UUID.randomUUID().toString();
        }
        requestBody.field(ParamConstants.F_FILE_NAME, fileName);

        return requestBody;
    }

    private void closeDataSources(InputStream data, MultiPart multiPartData) throws IOException {
        IOUtils.closeQuietly(data);
        if (multiPartData != null) {
            multiPartData.close();
        }
    }
}
