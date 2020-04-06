package eu.europeana.cloud.service.dps.utils.files.counter;

import com.google.common.base.Throwables;
import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.InputDataType;
import eu.europeana.cloud.service.dps.OAIPMHHarvestingDetails;
import eu.europeana.cloud.service.dps.rest.exceptions.TaskSubmissionException;
import eu.europeana.cloud.service.dps.storm.utils.OAIResponseParseException;
import org.dspace.xoai.model.oaipmh.Verb;
import org.dspace.xoai.serviceprovider.client.HttpOAIClient;
import org.dspace.xoai.serviceprovider.exceptions.OAIRequestException;
import org.dspace.xoai.serviceprovider.parameters.ListIdentifiersParameters;
import org.dspace.xoai.serviceprovider.parameters.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import javax.xml.xpath.*;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts the number of records to harvest from a specified OAI-PMH repository.
 */
public class OaiPmhFilesCounter extends FilesCounter {

    private final static Logger LOGGER = LoggerFactory.getLogger(OaiPmhFilesCounter.class);

    private static final String COMPLETE_LIST_SIZE_XPATH =
            "/*[local-name()='OAI-PMH']" +
                    "/*[local-name()='ListIdentifiers']" +
                    "/*[local-name()='resumptionToken']";
    public static final String COMPLETE_LIST_SIZE = "completeListSize";
    private static final int DEFAULT_LIST_SIZE = -1;
    private static final int DEFAULT_RETRIES = 3;
    private static final int SLEEP_TIME = 5000;

    /**
     * Returns the number of records to harvest. Executes ListIdentifiers request on OAI endpoint and extracts
     * completeListSize attribute (of resumptionToken node). When multiple schemas are specified, it sums the sizes
     * for all of them.
     * <p>
     * <p>
     * Accurate results are not available in special scenarios:
     * <ul>
     * <li>OAI response is not available, cannot be parsed or completeListSize attribute is not present</li>
     * <li><b></b>excludedSets</b> parameter is specified in <b>task</b></li>
     * <li><b></b>excludedSchemas</b> parameter is specified in <b>task</b></li>
     * <li><b></b>sets</b> parameter has multiple values<b>task</b></li>
     * </ul>
     * In case any of them happens, -1 is returned.
     *
     * @param task dps task
     * @return total number of records for given parameters or -1 if no value available in OAI
     * @throws TaskSubmissionException
     */
    @Override
    public int getFilesCount(DpsTask task) throws TaskSubmissionException {

        OAIPMHHarvestingDetails harvestingDetails = task.getHarvestingDetails();
        if (harvestingDetails != null) {
            if (specified(harvestingDetails.getExcludedSets()) || specified(harvestingDetails.getExcludedSchemas())) {
                LOGGER.info("Cannot count completeListSize for taskId= {} . Excluded sets or schemas are not supported", task.getTaskId());
                return DEFAULT_LIST_SIZE;
            }

            String repositoryUrl = getRepositoryUrl(task.getInputData());
            if (repositoryUrl != null) {
                ListIdentifiersParameters params = new ListIdentifiersParameters()
                        .withFrom(harvestingDetails.getDateFrom())
                        .withUntil(harvestingDetails.getDateUntil());

                Set<String> schemas = harvestingDetails.getSchemas();
                Set<String> sets = harvestingDetails.getSets();

                if (specified(sets)) {
                    if (sets.size() == 1) {
                        params.withSetSpec(sets.iterator().next());
                    } else {
                        LOGGER.info("Cannot count completeListSize for taskId= {} . Specifying multiple sets is not supported ", task.getTaskId());
                        return DEFAULT_LIST_SIZE;
                    }
                }

                try {
                    return getListSizeForSchemasAndSet(repositoryUrl, params, schemas);
                } catch (OAIResponseParseException e) {
                    LOGGER.info("Cannot count completeListSize for taskId= {}", task.getTaskId(), e);
                    return DEFAULT_LIST_SIZE;
                } catch (OAIRequestException e) {
                    String logMessage = "Cannot complete the request for the following repository URL " + repositoryUrl;
                    LOGGER.info(logMessage, e);
                    throw new TaskSubmissionException(logMessage + " Because: " + e.getMessage(), e);
                }
            } else {
                throw new TaskSubmissionException("The task was dropped because the repositoryUrl can not be null");
            }
        } else
            return DEFAULT_LIST_SIZE;
    }

    private String getRepositoryUrl(Map<InputDataType, List<String>> inputData) {
        List<String> urls = inputData.get(InputDataType.REPOSITORY_URLS);
        if (urls != null && !urls.isEmpty()) {
            return urls.get(0);
        }
        return null;
    }

    private int getListSizeForSchemasAndSet(String repositoryUrl, ListIdentifiersParameters params, Set<String> schemas) throws OAIRequestException, OAIResponseParseException {
        int sum = 0;
        if (specified(schemas)) {
            for (String schema : schemas) {
                params.withMetadataPrefix(schema);
                sum += getSizeForSchemaAndSet(repositoryUrl, params);
            }
        } else {
            sum = getSizeForSchemaAndSet(repositoryUrl, params);
        }
        return sum;
    }

    private int getSizeForSchemaAndSet(String repositoryUrl, ListIdentifiersParameters params) throws OAIResponseParseException, OAIRequestException {
        int retries = DEFAULT_RETRIES;
        while (true) {
            try {
                HttpOAIClient client = new HttpOAIClient(repositoryUrl);
                InputStream listIdentifiersResponse = client.execute(Parameters.parameters().withVerb(Verb.Type.ListIdentifiers).include(params));
                return readCompleteListSizeFromXML(listIdentifiersResponse);
            } catch (OAIRequestException e) {
                if (retries-- > 0) {
                    LOGGER.warn("Checking CompleteListSize. Retries left: {} ", retries);
                    try {
                        Thread.sleep(SLEEP_TIME);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        LOGGER.error(Throwables.getStackTraceAsString(e));
                    }
                } else {
                    throw e;
                }
            }
        }
    }

    private int readCompleteListSizeFromXML(InputStream stream) throws OAIResponseParseException {
        final InputSource inputSource = new SAXSource(new InputSource(stream)).getInputSource();
        final XPathExpression expr;
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            expr = xpath.compile(COMPLETE_LIST_SIZE_XPATH);

            Node resumptionTokenNode = (Node) expr.evaluate(inputSource, XPathConstants.NODE);
            if (resumptionTokenNode != null) {
                Node node = resumptionTokenNode.getAttributes().getNamedItem(COMPLETE_LIST_SIZE);
                if (node != null) {
                    String completeListSize = node.getNodeValue();
                    return Integer.parseInt(completeListSize);
                } else
                    throw new OAIResponseParseException("Cannot read completeListSize from OAI response. No resumption token node.");
            } else
                throw new OAIResponseParseException("Cannot read completeListSize from OAI response. No resumption token node.");
        } catch (XPathExpressionException | NumberFormatException e) {
            throw new OAIResponseParseException("Cannot read completeListSize from OAI response ", e);
        }
    }

    private boolean specified(Set<String> strings) {
        if (strings == null || strings.isEmpty()) {
            return false;
        }
        return true;
    }


}