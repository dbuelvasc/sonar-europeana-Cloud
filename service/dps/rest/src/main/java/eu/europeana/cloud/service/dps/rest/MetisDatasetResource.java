package eu.europeana.cloud.service.dps.rest;

import eu.europeana.cloud.common.model.dps.MetisDataset;
import eu.europeana.cloud.service.dps.metis.indexing.TargetIndexingDatabase;
import eu.europeana.cloud.service.dps.services.MetisDatasetService;
import eu.europeana.cloud.service.dps.storm.utils.HarvestedRecord;
import eu.europeana.indexing.exception.IndexingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import static eu.europeana.cloud.service.dps.RestInterfaceConstants.METIS_DATASETS;
import static eu.europeana.cloud.service.dps.RestInterfaceConstants.METIS_DATASET_PUBLISHED_RECORDS_SEARCH;

@RestController
public class MetisDatasetResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetisDatasetResource.class);

    private final MetisDatasetService metisDatasetService;

    public MetisDatasetResource(MetisDatasetService metisDatasetService){
        this.metisDatasetService = metisDatasetService;
    }
    @GetMapping(path = METIS_DATASETS, produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public MetisDataset getMetisDatasetStats(@PathVariable String datasetId,
                                             @RequestParam(value = "database") TargetIndexingDatabase targetIndexingDatabase) throws IndexingException {
        LOGGER.info("Reading dataset stats for datasetId: {}", datasetId);
        MetisDataset metisDataset = MetisDataset.builder()
                .id(datasetId)
                .build();

        return metisDatasetService.prepareStatsFor(metisDataset, targetIndexingDatabase/*, targetIndexingEnvironment*/);
    }

    /**
     * Search for the published record identifiers in the dataset that are on the list of record identifiers specified in the method param.
     *
     * @param datasetId         identifier of the dataset that should be examined
     * @param recordIdentifiers list of record identifiers that will be used for the examination
     * @return list of record identifiers that are on the recordIdentifiers list from the given dataset.
     */
    @PostMapping(path = METIS_DATASET_PUBLISHED_RECORDS_SEARCH, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> searchPublishedDatasetRecords(@PathVariable String datasetId, @RequestBody List<String> recordIdentifiers) {
        LOGGER.info("Searching for the published records in {} dataset", datasetId);

        return metisDatasetService.findPublishedRecordsInSet(
                        MetisDataset.builder()
                                .id(datasetId)
                                .build(),
                        recordIdentifiers)
                .stream()
                .map(HarvestedRecord::getRecordLocalId)
                .collect(Collectors.toList());
    }
}
