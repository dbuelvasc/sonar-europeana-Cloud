package eu.europeana.cloud.service.uis.dao;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import eu.europeana.cloud.common.annotation.Retryable;
import eu.europeana.cloud.common.model.CloudId;
import eu.europeana.cloud.common.model.IdentifierErrorInfo;
import eu.europeana.cloud.common.model.LocalId;
import eu.europeana.cloud.service.uis.exception.DatabaseConnectionException;
import eu.europeana.cloud.service.uis.status.IdentifierErrorTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * DAO providing access to operations on LocalId in the database
 */
public class LocalIdDAO {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalIdDAO.class);
    private final CassandraConnectionProvider dbService;
    private PreparedStatement insertStatement;
    private PreparedStatement deleteStatement;
    private PreparedStatement searchByRecordIdStatement;

    /**
     * The LocalId Dao
     *
     * @param dbService The service that exposes the database connection
     */
    public LocalIdDAO(CassandraConnectionProvider dbService) {
        this.dbService = dbService;
        prepareStatements();
    }

    private void prepareStatements() {
        insertStatement = dbService.getSession().prepare(
                "INSERT INTO cloud_ids_by_record_id(provider_id, record_id, cloud_id) VALUES(?,?,?)");

        deleteStatement = dbService.getSession().prepare(
                "DELETE FROM cloud_ids_by_record_id WHERE provider_id = ? AND record_id = ?");

        searchByRecordIdStatement = dbService.getSession().prepare(
                "SELECT * FROM cloud_ids_by_record_id WHERE provider_id = ? AND record_id = ?");
    }

    @Retryable
    public Optional<CloudId> searchById(String providerId, String recordId) throws DatabaseConnectionException {
        LOGGER.debug("Searching cloudId for providerId={} and recordId={}", providerId, recordId);
        try {
            Row row = dbService.getSession().execute(searchByRecordIdStatement.bind(providerId, recordId)).one();
            if (row != null) {
                CloudId cloudId = createCloudIdFromProviderRecordRow(row);
                LOGGER.debug("Found cloudId={} for providerId={} and recordId={}", cloudId, providerId, recordId);
                return Optional.of(cloudId);
            } else {
                LOGGER.debug("CloudId not found for providerId={} and recordId={}", providerId, recordId);
                return Optional.empty();
            }
        } catch (NoHostAvailableException e) {
            throw new DatabaseConnectionException(new IdentifierErrorInfo(
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getHttpCode(),
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getErrorInfo(dbService.getHosts(), dbService.getPort(), e.getMessage())));
        }
    }

    public CloudId insert(String providerId, String recordId, String cloudId) throws DatabaseConnectionException {
        try {
            dbService.getSession().execute(bindInsertStatement(providerId, recordId, cloudId));
        } catch (NoHostAvailableException e) {
            throw new DatabaseConnectionException(new IdentifierErrorInfo(
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getHttpCode(),
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getErrorInfo(
                            dbService.getHosts(), dbService.getPort(), e.getMessage())
            ));
        }

        return new CloudId(cloudId, new LocalId(providerId, recordId));
    }

    public BoundStatement bindInsertStatement(String providerId, String recordId, String cloudId) {
        return insertStatement.bind(providerId, recordId, cloudId);
    }


    public void delete(String providerId, String recordId) throws DatabaseConnectionException {
        try {
            dbService.getSession().execute(deleteStatement.bind(providerId, recordId));
        } catch (NoHostAvailableException e) {
            throw new DatabaseConnectionException(new IdentifierErrorInfo(
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getHttpCode(),
                    IdentifierErrorTemplate.DATABASE_CONNECTION_ERROR.getErrorInfo(
                            dbService.getHosts(), dbService.getPort(), e.getMessage())
            ));
        }
    }

    private CloudId createCloudIdFromProviderRecordRow(Row row) {
        LocalId lId = new LocalId();
        lId.setProviderId(row.getString("provider_Id"));
        lId.setRecordId(row.getString("record_Id"));
        CloudId cloudId = new CloudId();
        cloudId.setId(row.getString("cloud_id"));
        cloudId.setLocalId(lId);
        return cloudId;
    }
}