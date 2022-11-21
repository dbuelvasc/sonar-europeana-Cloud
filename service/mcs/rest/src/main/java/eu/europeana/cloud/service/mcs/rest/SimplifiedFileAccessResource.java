package eu.europeana.cloud.service.mcs.rest;

import eu.europeana.cloud.client.uis.rest.CloudException;
import eu.europeana.cloud.common.model.CloudId;
import eu.europeana.cloud.common.model.File;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.common.selectors.LatestPersistentRepresentationVersionSelector;
import eu.europeana.cloud.common.selectors.RepresentationSelector;
import eu.europeana.cloud.service.mcs.RecordService;
import eu.europeana.cloud.service.mcs.UISClientHandler;
import eu.europeana.cloud.service.mcs.exception.*;
import eu.europeana.cloud.service.mcs.utils.EnrichUriUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;

import static eu.europeana.cloud.service.mcs.RestInterfaceConstants.SIMPLIFIED_FILE_ACCESS_RESOURCE;

/**
 * Gives (read) access to files stored in ecloud in simplified (friendly) way. <br/> The latest persistent version of
 * representation is picked up.
 */
@RestController
@RequestMapping(SIMPLIFIED_FILE_ACCESS_RESOURCE)
public class SimplifiedFileAccessResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimplifiedFileAccessResource.class);

  private final RecordService recordService;
  private final UISClientHandler uisClientHandler;

  public SimplifiedFileAccessResource(RecordService recordService, UISClientHandler uisClientHandler) {
    this.recordService = recordService;
    this.uisClientHandler = uisClientHandler;
  }

  /**
   * Returns file content from <b>latest persistent version</b> of specified representation.
   *
   * @param providerId providerId
   * @param localId localId
   * @param representationName representationName
   * @param fileName fileName
   * @return Requested file context
   * @throws RepresentationNotExistsException
   * @throws FileNotExistsException
   * @throws CloudException
   * @throws RecordNotExistsException
   * @summary Get file content using simplified url
   * @statuscode 204 object has been updated.
   */
  @GetMapping
  public ResponseEntity<StreamingResponseBody> getFile(
      HttpServletRequest httpServletRequest,
      @PathVariable final String providerId,
      @PathVariable final String localId,
      @PathVariable final String representationName,
      @PathVariable final String fileName) throws RepresentationNotExistsException,
      FileNotExistsException, RecordNotExistsException, ProviderNotExistsException, WrongContentRangeException {

    LOGGER.info("Reading file in friendly way for: provider: {}, localId: {}, represenatation: {}, fileName: {}",
        providerId, localId, representationName, fileName);

    final String cloudId = findCloudIdFor(providerId, localId);
    final Representation representation = selectRepresentationVersion(cloudId, representationName);
    if (representation == null) {
      throw new RepresentationNotExistsException();
    }

    final File requestedFile = readFile(cloudId, representationName, representation.getVersion(), fileName);

    String md5 = requestedFile.getMd5();
    MediaType fileMimeType = null;
    if (StringUtils.isNotBlank(requestedFile.getMimeType())) {
      fileMimeType = MediaType.parseMediaType(requestedFile.getMimeType());
    }
    EnrichUriUtil.enrich(httpServletRequest, representation, requestedFile);
    final FileResource.ContentRange contentRange = new FileResource.ContentRange(-1L, -1L);
    Consumer<OutputStream> downloadingMethod = recordService.getContent(cloudId, representationName, representation.getVersion(),
        fileName, contentRange.getStart(), contentRange.getEnd());

    ResponseEntity.BodyBuilder response = ResponseEntity
        .status(HttpStatus.OK)
        .location(requestedFile.getContentUri());
    if (md5 != null) {
      response.eTag(md5);
    }
    if (fileMimeType != null) {
      response.contentType(fileMimeType);
    }
    return response.body(output -> downloadingMethod.accept(output));
  }

  /**
   * Returns file headers from <b>latest persistent version</b> of specified representation.
   *
   * @param httpServletRequest
   * @param providerId providerId
   * @param localId localId
   * @param representationName representationName
   * @param fileName fileNAme
   * @return Requested file headers (together with full file path in 'Location' header)
   * @throws RepresentationNotExistsException
   * @throws FileNotExistsException
   * @throws CloudException
   * @throws RecordNotExistsException
   * @throws ProviderNotExistsException
   * @summary Get file headers using simplified url
   */
  @RequestMapping(method = RequestMethod.HEAD)
  public ResponseEntity<?> getFileHeaders(
      HttpServletRequest httpServletRequest,
      @PathVariable final String providerId,
      @PathVariable final String localId,
      @PathVariable final String representationName,
      @PathVariable final String fileName) throws RepresentationNotExistsException,
      FileNotExistsException, RecordNotExistsException, ProviderNotExistsException {

    LOGGER.info("Reading file headers in friendly way for: provider: {}, localId: {}, represenatation: {}, fileName: {}",
        providerId, localId, representationName, fileName);

    final String cloudId = findCloudIdFor(providerId, localId);
    final Representation representation = selectRepresentationVersion(cloudId, representationName);
    if (representation == null) {
      throw new RepresentationNotExistsException();
    }

    final File requestedFile = readFile(cloudId, representationName, representation.getVersion(), fileName);

    String md5 = requestedFile.getMd5();
    MediaType fileMimeType = null;
    if (StringUtils.isNotBlank(requestedFile.getMimeType())) {
      fileMimeType = MediaType.parseMediaType(requestedFile.getMimeType());
    }
    EnrichUriUtil.enrich(httpServletRequest, representation, requestedFile);

    ResponseEntity.BodyBuilder response = ResponseEntity
        .status(HttpStatus.OK)
        .location(requestedFile.getContentUri());
    if (md5 != null) {
      response.eTag(md5);
    }
    if (fileMimeType != null) {
      response.contentType(fileMimeType);
    }
    return response.build();
  }

  private String findCloudIdFor(String providerID, String localId) throws ProviderNotExistsException, RecordNotExistsException {
    CloudId foundCloudId = uisClientHandler.getCloudIdFromProviderAndLocalId(providerID, localId);
    return foundCloudId.getId();
  }

  private Representation selectRepresentationVersion(String cloudId, String representationName)
      throws RepresentationNotExistsException {
    List<Representation> representations = recordService.listRepresentationVersions(cloudId, representationName);
    RepresentationSelector representationSelector = new LatestPersistentRepresentationVersionSelector();
    return representationSelector.select(representations);
  }

  private File readFile(String cloudId, String representationName, String version, String fileName)
      throws RepresentationNotExistsException, FileNotExistsException {

    return recordService.getFile(cloudId, representationName, version, fileName);
  }

}
