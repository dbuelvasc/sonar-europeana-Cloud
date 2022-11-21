package eu.europeana.cloud.service.mcs.rest;

import eu.europeana.cloud.common.model.File;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.service.mcs.RecordService;
import eu.europeana.cloud.service.mcs.Storage;
import eu.europeana.cloud.service.mcs.exception.*;
import eu.europeana.cloud.service.mcs.utils.EnrichUriUtil;
import eu.europeana.cloud.service.mcs.utils.storage_selector.PreBufferedInputStream;
import eu.europeana.cloud.service.mcs.utils.storage_selector.StorageSelector;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static eu.europeana.cloud.service.mcs.RestInterfaceConstants.FILE_UPLOAD_RESOURCE;

/**
 * Handles uploading the file when representation is not created yet.
 */
@RestController
@RequestMapping(FILE_UPLOAD_RESOURCE)
public class FileUploadResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadResource.class);
  private final RecordService recordService;
  private final Integer objectStoreSizeThreshold;

  public FileUploadResource(
      RecordService recordService,
      Integer objectStoreSizeThreshold) {
    this.recordService = recordService;
    this.objectStoreSizeThreshold = objectStoreSizeThreshold;
  }

  /**
   * Creates representation, uploads file and persists this representation in one request
   *
   * @param cloudId cloudId
   * @param representationName representation name
   * @param fileName file name
   * @param providerId providerId
   * @param mimeType mimeType of uploaded file
   * @param data uploaded file content
   * @return result of the operation. Usually it is OK containing created file location but maybe also exception.
   * @throws RepresentationNotExistsException
   * @throws CannotModifyPersistentRepresentationException
   * @throws RecordNotExistsException
   * @throws ProviderNotExistsException
   * @throws CannotPersistEmptyRepresentationException
   * @summary Upload file for non-existing representation
   */
  @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @PreAuthorize("hasRole('ROLE_EXECUTOR') OR  hasPermission(#dataSetId.concat('/').concat(#providerId), 'eu.europeana.cloud.common.model.DataSet', write)")
  public ResponseEntity<?> sendFile(
      HttpServletRequest httpServletRequest,
      @PathVariable String cloudId,
      @PathVariable String representationName,
      @RequestParam(required = false) UUID version,
      @RequestParam String fileName,
      @RequestParam String providerId,
      @RequestParam String mimeType,
      @RequestParam String dataSetId,
      @RequestParam MultipartFile data) throws RepresentationNotExistsException,
      CannotModifyPersistentRepresentationException, RecordNotExistsException,
      ProviderNotExistsException, CannotPersistEmptyRepresentationException, IOException, DataSetAssignmentException, DataSetNotExistsException {
    LOGGER.debug("Uploading file cloudId={}, representationName={}, version={}, fileName={}, providerId={}, mime={}",
        cloudId, representationName, version, fileName, providerId, mimeType);
    PreBufferedInputStream prebufferedInputStream = new PreBufferedInputStream(data.getInputStream(), objectStoreSizeThreshold);
    Storage storage = new StorageSelector(prebufferedInputStream, mimeType).selectStorage();
    LOGGER.trace("File {} buffered", fileName);
    Representation representation = recordService.createRepresentation(cloudId, representationName, providerId, version,
        dataSetId);

    File file = addFileToRepresentation(representation, prebufferedInputStream, mimeType, fileName, storage);
    persistRepresentation(representation);

    EnrichUriUtil.enrich(httpServletRequest, cloudId, representationName, representation.getVersion(), file);

    return ResponseEntity
        .created(file.getContentUri())
        .eTag(file.getMd5())
        .build();
  }

  private File addFileToRepresentation(
      Representation representation, InputStream data, String mimeType, String fileName, Storage storage)
      throws RepresentationNotExistsException, CannotModifyPersistentRepresentationException {

    File f = new File();
    f.setMimeType(mimeType);
    f.setFileStorage(storage);

    if (fileName == null) {
      fileName = UUID.randomUUID().toString();
    }
    f.setFileName(fileName);
    recordService.putContent(representation.getCloudId(), representation.getRepresentationName(),
        representation.getVersion(), f, data);
    IOUtils.closeQuietly(data);
    return f;
  }

  private void persistRepresentation(Representation representation) throws
      CannotModifyPersistentRepresentationException,
      CannotPersistEmptyRepresentationException,
      RepresentationNotExistsException {
    recordService.persistRepresentation(representation.getCloudId(), representation.getRepresentationName(),
        representation.getVersion());
    LOGGER.debug("Representation persisted: {}", representation);
  }
}
