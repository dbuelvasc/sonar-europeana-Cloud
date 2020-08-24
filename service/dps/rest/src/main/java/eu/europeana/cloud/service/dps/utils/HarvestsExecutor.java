package eu.europeana.cloud.service.dps.utils;

import eu.europeana.cloud.common.model.dps.ProcessedRecord;
import eu.europeana.cloud.common.model.dps.RecordState;
import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.cloud.service.dps.*;
import eu.europeana.cloud.service.dps.oaipmh.Harvester;
import eu.europeana.cloud.service.dps.oaipmh.HarvesterException;
import eu.europeana.cloud.service.dps.oaipmh.HarvesterFactory;
import eu.europeana.cloud.service.dps.storm.utils.ProcessedRecordsDAO;
import eu.europeana.cloud.service.dps.storm.utils.TaskStatusChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
public class HarvestsExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(HarvestsExecutor.class);

    private static final int DEFAULT_RETRIES = 3;
    private static final int SLEEP_TIME = 5000;

    private final RecordExecutionSubmitService recordSubmitService;
    private final ProcessedRecordsDAO processedRecordsDAO;
    /**
     * Auxiliary object to check 'kill flag' for task
     */
    private final TaskStatusChecker taskStatusChecker;

    public HarvestsExecutor(RecordExecutionSubmitService recordSubmitService, ProcessedRecordsDAO processedRecordsDAO, TaskStatusChecker taskStatusChecker) {
        this.recordSubmitService = recordSubmitService;
        this.processedRecordsDAO = processedRecordsDAO;
        this.taskStatusChecker = taskStatusChecker;
    }

    public HarvestResult execute(String topologyName, List<Harvest> harvestsToBeExecuted, DpsTask dpsTask, String topicName) throws HarvesterException {
        int resultCounter = 0;

        for (Harvest harvest : harvestsToBeExecuted) {
            LOGGER.info("(Re-)starting identifiers harvesting for: {}. Task identifier: {}", harvest, dpsTask.getTaskId());
            Harvester harvester = HarvesterFactory.createHarvester(DEFAULT_RETRIES, SLEEP_TIME);
            Iterator<OAIHeader> headerIterator = harvester.harvestIdentifiers(harvest);

            // *** Main harvesting loop for given task ***
            while (headerIterator.hasNext()) {
                if (taskStatusChecker.hasKillFlag(dpsTask.getTaskId())) {
                    LOGGER.info("Harvesting for {} (Task: {}) stopped by external signal", harvest, dpsTask.getTaskId());
                    return HarvestResult.builder()
                            .resultCounter(resultCounter)
                            .taskState(TaskState.DROPPED).build();
                }

                OAIHeader oaiHeader = headerIterator.next();
                DpsRecord record = convertToDpsRecord(oaiHeader, harvest, dpsTask);

                sendMessage(record, topicName);
                updateRecordStatus(record, topologyName);
                logProgressFor(harvest, resultCounter);
                resultCounter++;
            }
            LOGGER.info("Identifiers harvesting finished for: {}. Counter: {}", harvest, resultCounter);
        }
        return new HarvestResult(resultCounter, TaskState.QUEUED);
    }

    /*Merge code below when latest version of restart procedure will be done/known*/
    public HarvestResult executeForRestart(String topologyName, List<Harvest> harvestsToByExecuted, DpsTask dpsTask, String topicName) throws HarvesterException {
        int resultCounter = 0;

        for (Harvest harvest : harvestsToByExecuted) {
            LOGGER.info("(Re-)starting identifiers harvesting for: {}. Task identifier: {}", harvest, dpsTask.getTaskId());
            Harvester harvester = HarvesterFactory.createHarvester(DEFAULT_RETRIES, SLEEP_TIME);
            Iterator<OAIHeader> headerIterator = harvester.harvestIdentifiers(harvest);

            // *** Main harvesting loop for given task ***
            while (headerIterator.hasNext()) {
                if (taskStatusChecker.hasKillFlag(dpsTask.getTaskId())) {
                    LOGGER.info("Harvesting for {} (Task: {}) stopped by external signal", harvest, dpsTask.getTaskId());
                    return HarvestResult.builder()
                            .resultCounter(resultCounter)
                            .taskState(TaskState.DROPPED).build();
                }

                OAIHeader oaiHeader = headerIterator.next();

                Optional<ProcessedRecord> processedRecord = processedRecordsDAO.selectByPrimaryKey(dpsTask.getTaskId(), oaiHeader.getIdentifier());
                if (processedRecord.isEmpty() || processedRecord.get().getState() == RecordState.ERROR) {
                    DpsRecord record = convertToDpsRecord(oaiHeader, harvest, dpsTask);
                    sendMessage(record, topicName);
                    updateRecordStatus(record, topologyName);
                    resultCounter++;
                }
            }
            LOGGER.info("Identifiers harvesting finished for: {}. Counter: {}", harvest, resultCounter);
        }
        return new HarvestResult(resultCounter, TaskState.QUEUED);
    }

    /*package visiblility*/ DpsRecord convertToDpsRecord(OAIHeader oaiHeader, Harvest harvest, DpsTask dpsTask) {
        return DpsRecord.builder()
                .taskId(dpsTask.getTaskId())
                .recordId(oaiHeader.getIdentifier())
                .metadataPrefix(harvest.getMetadataPrefix())
                .build();
    }

    /*package visiblility*/ void sendMessage(DpsRecord record, String topicName) {
        LOGGER.debug("Sending records to messages queue: {}", record);
        recordSubmitService.submitRecord(record, topicName);
    }

    /*package visiblility*/  void updateRecordStatus(DpsRecord dpsRecord, String topologyName) {
        int attemptNumber = processedRecordsDAO.getAttemptNumber(dpsRecord.getTaskId(), dpsRecord.getRecordId());

        LOGGER.debug("Updating record in notifications table: {}", dpsRecord);
        processedRecordsDAO.insert(dpsRecord.getTaskId(), dpsRecord.getRecordId(), attemptNumber,
                "", topologyName, RecordState.QUEUED.toString(), "", "");
    }

    /*package visiblility*/ void logProgressFor(Harvest harvest, int counter) {
        if (counter % 1000 == 0) {
            LOGGER.info("Identifiers harvesting is progressing for: {}. Current counter: {}", harvest, counter);
        }
    }
}
