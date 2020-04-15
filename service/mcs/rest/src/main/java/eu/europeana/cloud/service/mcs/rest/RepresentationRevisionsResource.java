package eu.europeana.cloud.service.mcs.rest;

import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.common.response.RepresentationRevisionResponse;
import eu.europeana.cloud.service.mcs.RecordService;
import eu.europeana.cloud.service.mcs.exception.RepresentationNotExistsException;
import eu.europeana.cloud.service.mcs.utils.EnrichUriUtil;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Resource to manage representations.
 */
@RestController
@RequestMapping("/records/{cloudId}/representations/{representationName}/revisions/{revisionName}")
@Scope("request")
public class RepresentationRevisionsResource {

    @Autowired
    private RecordService recordService;

    @Autowired
    private PermissionEvaluator permissionEvaluator;

    /**
     * Returns the representation version which associates cloud identifier, representation name with revision identifier, provider and timestamp.
     * <strong>Read permissions required.</strong>
     *
     * @param cloudId           cloud id of the record which contains the representation .
     * @param representationName             name of the representation .
     * @param revisionName       name of the revision associated with this representation version
     * @param revisionProviderId identifier of institution that provided the revision
     * @param revisionTimestamp  timestamp of the specific revision, if not given the latest revision with revisionName
     *                           created by revisionProviderId will be considered (timestamp should be given in UTC format)
     * @return requested specific representation object.
     * @throws RepresentationNotExistsException when representation doesn't exist
     * @summary get a representation response object
     */
    @GetMapping(produces = {MediaType.APPLICATION_XML_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public @ResponseBody List<Representation> getRepresentationRevisions(
            HttpServletRequest httpServletRequest,
            @PathVariable String cloudId,
            @PathVariable String representationName,
            @PathVariable String revisionName,
            @RequestParam String revisionProviderId,
            @RequestParam(required = false) String revisionTimestamp) throws RepresentationNotExistsException {

        Date revisionDate = null;
        if (revisionTimestamp != null) {
            DateTime utc = new DateTime(revisionTimestamp, DateTimeZone.UTC);
            revisionDate = utc.toDate();
        }
        List<RepresentationRevisionResponse> info =
                recordService.getRepresentationRevisions(cloudId, representationName, revisionProviderId, revisionName, revisionDate);
        List<Representation> representations = new ArrayList<>();
        if (info != null) {
            for (RepresentationRevisionResponse representationRevisionsResource : info) {
                Representation representation;
                representation = recordService.getRepresentation(
                        representationRevisionsResource.getCloudId(),
                        representationRevisionsResource.getRepresentationName(),
                        representationRevisionsResource.getVersion());
                EnrichUriUtil.enrich(httpServletRequest, representation);
                //
                if (userHasAccessTo(representation)) {
                    representations.add(representation);
                }
            }
        } else
            throw new RepresentationNotExistsException("No representation was found");

        return representations;
    }

    private boolean userHasAccessTo(Representation representation){
        SecurityContext ctx = SecurityContextHolder.getContext();
        Authentication authentication = ctx.getAuthentication();
        //
        String targetId = representation.getCloudId() + "/" + representation.getRepresentationName() + "/" + representation.getVersion();
        return permissionEvaluator.hasPermission(authentication, targetId, Representation.class.getName(), "read");
    }

}
