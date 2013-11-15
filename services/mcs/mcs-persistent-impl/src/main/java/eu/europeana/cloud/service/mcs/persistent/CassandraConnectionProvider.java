package eu.europeana.cloud.service.mcs.persistent;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.ShutdownFuture;

/**
 * CassandraConnectionProvider
 */
@Service
public class CassandraConnectionProvider {

    private final static Logger log = LoggerFactory.getLogger(CassandraConnectionProvider.class);

    private final Cluster cluster;

    private final Session session;


    public CassandraConnectionProvider(String host, int port, String keyspaceName) {
        cluster = Cluster.builder().addContactPoint(host).withPort(port).build();
        Metadata metadata = cluster.getMetadata();
        log.info("Connected to cluster: {}", metadata.getClusterName());
        for (Host h : metadata.getAllHosts()) {
            log.info("Datatacenter: {}; Host: {}; Rack: {}", h.getDatacenter(), h.getAddress(), h.getRack());
        }
        session = cluster.connect(keyspaceName);
    }


    @PreDestroy
    public void closeConnections() {
        log.info("Cluster is shutting down.");
        ShutdownFuture shutdownFuture = cluster.shutdown();
    }


    public Session getSession() {
        return session;
    }
}