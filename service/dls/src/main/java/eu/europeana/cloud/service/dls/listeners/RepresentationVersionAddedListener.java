package eu.europeana.cloud.service.dls.listeners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.service.dls.solr.SolrDAO;
import java.io.IOException;
import org.apache.solr.client.solrj.SolrServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Listener class which reacts on messages about
 */
@Component
public class RepresentationVersionAddedListener implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepresentationVersionAddedListener.class);
    private static final Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ")
            .create();

    @Autowired
    SolrDAO solrDao;

    /**
     *
     * @param message
     */
    @Override
    public void onMessage(Message message) {
        String messageBody = message.getBody().toString();
        if (messageBody == null) {
            LOGGER.error("Message has null body");
            return;
        }
        if (messageBody.equals("")) {
            LOGGER.error("Message has empty body");
        }

        Representation representation = gson.fromJson(message.getBody().toString(), Representation.class);
        
        try {
            solrDao.insertRepresentation(representation, null);
        } catch (IOException | SolrServerException ex) {
            LOGGER.error("Cannot insert representation into solr", ex);
        }
    }

}
