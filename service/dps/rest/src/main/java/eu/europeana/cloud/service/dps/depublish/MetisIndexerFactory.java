package eu.europeana.cloud.service.dps.depublish;

import eu.europeana.cloud.service.dps.metis.indexing.TargetIndexingEnvironment;
import eu.europeana.cloud.service.dps.service.utils.indexing.IndexingSettingsGenerator;
import eu.europeana.indexing.Indexer;
import eu.europeana.indexing.IndexerFactory;
import eu.europeana.indexing.exception.IndexingException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Properties;

@Service
public class MetisIndexerFactory {

    private final Properties properties = new Properties();

    public MetisIndexerFactory() throws IOException {
        loadProperties();
    }

    public Indexer openIndexer(boolean useAlternativeEnvironment) throws IndexingException, URISyntaxException {
        IndexerFactory indexerFactory = createMetisIndexerFactory(useAlternativeEnvironment);
        return indexerFactory.getIndexer();
    }

    private void loadProperties() throws IOException {
        InputStream input = MetisIndexerFactory.class.getClassLoader().getResourceAsStream(IndexingSettingsGenerator.DEFAULT_PROPERTIES_FILENAME);
        properties.load(input);
    }

    private IndexerFactory createMetisIndexerFactory(boolean useAlternativeEnvironment) throws IndexingException, URISyntaxException {
        IndexingSettingsGenerator settingsGenerator;
        if (useAlternativeEnvironment) {
            settingsGenerator = new IndexingSettingsGenerator(TargetIndexingEnvironment.ALTERNATIVE, properties);
        } else {
            settingsGenerator = new IndexingSettingsGenerator(properties);
        }
        return new IndexerFactory(settingsGenerator.generateForPublish());
    }

}