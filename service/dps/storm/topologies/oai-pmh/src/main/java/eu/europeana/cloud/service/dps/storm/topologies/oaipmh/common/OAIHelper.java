package eu.europeana.cloud.service.dps.storm.topologies.oaipmh.common;

import org.dspace.xoai.model.oaipmh.MetadataFormat;
import org.dspace.xoai.serviceprovider.ServiceProvider;
import org.dspace.xoai.serviceprovider.client.HttpOAIClient;
import org.dspace.xoai.serviceprovider.client.OAIClient;
import org.dspace.xoai.serviceprovider.exceptions.BadArgumentException;
import org.dspace.xoai.serviceprovider.exceptions.IdDoesNotExistException;
import org.dspace.xoai.serviceprovider.exceptions.InvalidOAIResponse;
import org.dspace.xoai.serviceprovider.model.Context;
import eu.europeana.cloud.service.dps.storm.AbstractDpsBolt;
import eu.europeana.cloud.service.dps.storm.topologies.oaipmh.bolt.IdentifiersHarvestingBolt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Iterator;

/**
 * Created by Tarek on 7/5/2017.
 */
public class OAIHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(OAIHelper.class);

    private ServiceProvider serviceProvider;

    public OAIHelper(String resourceURL) {
        OAIClient client = new HttpOAIClient(resourceURL);
        serviceProvider = new ServiceProvider(new Context().withOAIClient(client));
    }

    public Iterator<MetadataFormat> listSchemas() {
        int retries = AbstractDpsBolt.DEFAULT_RETRIES;

        while (true) {
            try {
                return serviceProvider.listMetadataFormats();
            }
            catch (InvalidOAIResponse e) {
                if (retries-- > 0) {
                    LOGGER.warn("Error retrieving metadata schemas. Retries left: " + retries);
                    try {
                        Thread.sleep(AbstractDpsBolt.SLEEP_TIME);
                    } catch (InterruptedException ex) {
                        LOGGER.error(ex.getMessage());
                    }
                }
                else {
                    LOGGER.error("Retrieving metadata schemas failed.");
                    throw e;
                }
            } catch (IdDoesNotExistException e) {
               //will never happen in here as we don't specify "identifier" argument
            }
        }
    }

    public Date getEarlierDate() {
        int retries = AbstractDpsBolt.DEFAULT_RETRIES;

        while (true) {
            try {
                return serviceProvider.identify().getEarliestDatestamp();
            }
            catch (InvalidOAIResponse e) {
                if (retries-- > 0) {
                    LOGGER.warn("Error retrieving the earliest timestamp. Retries left: " + retries);
                    try {
                        Thread.sleep(AbstractDpsBolt.SLEEP_TIME);
                    } catch (InterruptedException e1) {
                        LOGGER.error(e1.getMessage());
                    }
                }
                else {
                    LOGGER.error("Retrieving the earliest timestamp failed.");
                    throw e;
                }
            }
        }
    }
}
