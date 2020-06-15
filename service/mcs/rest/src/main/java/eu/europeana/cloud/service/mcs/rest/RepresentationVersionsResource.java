package eu.europeana.cloud.service.mcs.rest;

import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.service.mcs.RecordService;
import eu.europeana.cloud.service.mcs.exception.RepresentationNotExistsException;
import eu.europeana.cloud.service.mcs.utils.EnrichUriUtil;
import eu.europeana.cloud.service.mcs.utils.RepresentationsListWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

import static eu.europeana.cloud.service.mcs.RestInterfaceConstants.REPRESENTATION_VERSIONS_RESOURCE;

/**
 * Resource to manage representation versions.
 */
@RestController
@RequestMapping(REPRESENTATION_VERSIONS_RESOURCE)
public class RepresentationVersionsResource {

    public static final Logger LOGGER = LoggerFactory.getLogger(RepresentationVersionsResource.class.getName());

    private final RecordService recordService;

    public RepresentationVersionsResource(RecordService recordService) {
        this.recordService = recordService;
    }

    /**
     * Lists all versions of record representation. Temporary versions will be
     * included in the returned list.
     * @summary get all representation versions.
     *
     * @return list of all the representation versions.
     * @throws RepresentationNotExistsException representation does not exist.
     */
    @GetMapping(produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public RepresentationsListWrapper listVersions(
            final HttpServletRequest request,
            @PathVariable String cloudId,
            @PathVariable String representationName)
            throws RepresentationNotExistsException {

        List<Representation> representationVersions = recordService.listRepresentationVersions(cloudId, representationName);
        for (Representation representationVersion : representationVersions) {
            EnrichUriUtil.enrich(request, representationVersion);
        }

        return new RepresentationsListWrapper(representationVersions);
    }
}
