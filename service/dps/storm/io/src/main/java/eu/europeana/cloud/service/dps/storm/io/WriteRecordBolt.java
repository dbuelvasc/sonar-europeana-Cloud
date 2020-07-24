package eu.europeana.cloud.service.dps.storm.io;


import eu.europeana.cloud.client.uis.rest.CloudException;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.mcs.driver.RecordServiceClient;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.storm.AbstractDpsBolt;
import eu.europeana.cloud.service.dps.storm.StormTaskTuple;
import eu.europeana.cloud.service.dps.storm.utils.TaskTupleUtility;
import eu.europeana.cloud.service.mcs.exception.MCSException;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;

/**
 * Stores a Record on the cloud.
 * <p/>
 * Receives a byte array representing a Record from a tuple, creates and stores
 * a new Record on the cloud, and emits the URL of the newly created record.
 */
public class WriteRecordBolt extends AbstractDpsBolt {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteRecordBolt.class);

    private String ecloudMcsAddress;
    protected transient RecordServiceClient recordServiceClient;

    public WriteRecordBolt(String ecloudMcsAddress) {
        this.ecloudMcsAddress = ecloudMcsAddress;
    }

    @Override
    public void prepare() {
        recordServiceClient = new RecordServiceClient(ecloudMcsAddress);
    }

    @Override
    public void execute(Tuple anchorTuple, StormTaskTuple t) {
        try {
            LOGGER.info("WriteRecordBolt: persisting...");
            final URI uri = uploadFileInNewRepresentation(t);
            LOGGER.info("WriteRecordBolt: file modified, new URI: {}", uri);
            prepareEmittedTuple(t, uri.toString());

            outputCollector.emit(anchorTuple, t.toStormTuple());
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            StringWriter stack = new StringWriter();
            e.printStackTrace(new PrintWriter(stack));
            emitErrorNotification(anchorTuple, t.getTaskId(), t.getFileUrl(),
                    "Cannot process data because: " + e.getMessage(), stack.toString());
        } finally {
            System.err.println("^^^^^^^^^^^^^^^^^^ WRITE_RECORD: "+t.getTaskId()+" | "+t.getFileUrl()+" | "+anchorTuple.getMessageId());
            outputCollector.ack(anchorTuple);
        }
    }

    protected URI uploadFileInNewRepresentation(StormTaskTuple stormTaskTuple) throws IOException, MCSException, CloudException {
        return createRepresentationAndUploadFile(stormTaskTuple);
    }

    protected URI createRepresentationAndUploadFile(StormTaskTuple stormTaskTuple) throws IOException, MCSException, CloudException {
        int retries = DEFAULT_RETRIES;
        while (true) {
            try {
                return recordServiceClient.createRepresentation(
                        stormTaskTuple.getParameter(PluginParameterKeys.CLOUD_ID),
                        TaskTupleUtility.getParameterFromTuple(stormTaskTuple, PluginParameterKeys.NEW_REPRESENTATION_NAME),
                        getProviderId(stormTaskTuple), stormTaskTuple.getFileByteDataAsStream(),
                        stormTaskTuple.getParameter(PluginParameterKeys.OUTPUT_FILE_NAME),
                        TaskTupleUtility.getParameterFromTuple(stormTaskTuple, PluginParameterKeys.OUTPUT_MIME_TYPE),
                        AUTHORIZATION, stormTaskTuple.getParameter(PluginParameterKeys.AUTHORIZATION_HEADER));
            } catch (Exception e) {
                if (retries-- > 0) {
                    LOGGER.warn("Error while creating representation and uploading file. Retries left {}", retries);
                    waitForSpecificTime();
                } else {
                    LOGGER.error("Error while creating representation and uploading file.");
                    throw e;
                }
            }
        }
    }

    private String getProviderId(StormTaskTuple stormTaskTuple) throws MCSException {
        Representation rep = getRepresentation(stormTaskTuple);
        return rep.getDataProvider();
    }

    private Representation getRepresentation(StormTaskTuple stormTaskTuple) throws MCSException {
        int retries = DEFAULT_RETRIES;
        while (true) {
            try {
                return recordServiceClient.getRepresentation(stormTaskTuple.getParameter(PluginParameterKeys.CLOUD_ID), stormTaskTuple.getParameter(PluginParameterKeys.REPRESENTATION_NAME), stormTaskTuple.getParameter(PluginParameterKeys.REPRESENTATION_VERSION), AUTHORIZATION, stormTaskTuple.getParameter(PluginParameterKeys.AUTHORIZATION_HEADER));
            } catch (Exception e) {
                if (retries-- > 0) {
                    LOGGER.warn("Error while getting provider id. Retries left {}", retries);
                    waitForSpecificTime();
                } else {
                    LOGGER.error("Error while getting provider id. Retries left");
                    throw e;
                }
            }
        }
    }

    private void prepareEmittedTuple(StormTaskTuple stormTaskTuple, String resultedResourceURL) {
        stormTaskTuple.addParameter(PluginParameterKeys.OUTPUT_URL, resultedResourceURL);
        stormTaskTuple.setFileData((byte[]) null);
        stormTaskTuple.getParameters().remove(PluginParameterKeys.CLOUD_ID);
        stormTaskTuple.getParameters().remove(PluginParameterKeys.REPRESENTATION_NAME);
        stormTaskTuple.getParameters().remove(PluginParameterKeys.REPRESENTATION_VERSION);
    }


}

