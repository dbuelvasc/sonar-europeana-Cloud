package eu.europeana.cloud.service.mcs.rest;

import com.qmino.miredot.annotations.ReturnType;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.service.mcs.RecordService;
import eu.europeana.cloud.service.mcs.exception.RecordNotExistsException;
import eu.europeana.cloud.service.mcs.utils.EnrichUriUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.List;

import static eu.europeana.cloud.common.web.ParamConstants.P_CLOUDID;

/**
 * Resource that represents record representations.
 */
@RestController
@RequestMapping("/records/{cloudId}/representations")
@Scope("request")
public class RepresentationsResource {

    @Autowired
    private RecordService recordService;

    /**
     * Returns a list of all the latest persistent versions of a record representation.
     * @summary get representations
     * @param cloudId cloud id of the record in which all the latest versions of representations are required.
     * @return list of representations.
     * @throws RecordNotExistsException provided id is not known to Unique
     * Identifier Service.
     */
    @GetMapping(produces = {MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public @ResponseBody List<Representation> getRepresentations(
            HttpServletRequest httpServletRequest,
            @PathVariable String cloudId) throws RecordNotExistsException {

        List<Representation> representationInfos = recordService.getRecord(cloudId).getRepresentations();
        prepare(httpServletRequest, representationInfos);
        return representationInfos;
    }

    private void prepare(HttpServletRequest httpServletRequest, List<Representation> representationInfos) {
        for (Representation representationInfo : representationInfos) {
            representationInfo.setFiles(null);
            EnrichUriUtil.enrich(httpServletRequest, representationInfo);
        }
    }
}
