package migrations.service.mcs.V18.jobs;

import com.contrastsecurity.cassandra.migration.logging.Log;
import com.contrastsecurity.cassandra.migration.logging.LogFactory;
import com.datastax.driver.core.*;
import eu.europeana.cloud.common.utils.Bucket;
import eu.europeana.cloud.service.commons.utils.BucketsHandler;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.Callable;

import static migrations.common.TableCopier.hasNextRow;

/**
 * Created by Tarek on 5/16/2019.
 */
public class DataCopier implements Callable<String> {

  private static final Log LOG = LogFactory.getLog(DataCopier.class);

  private static final String PROVIDER_ID = "provider_id";
  private static final String DATASET_ID = "dataset_id";
  private static final int DEFAULT_RETRIES = 3;
  private static final int SLEEP_TIME = 5000;

  private Session session;

  private PreparedStatement selectStatement;
  private PreparedStatement insertStatement;

  private PreparedStatement bucketsForSpecificObjectIdStatement;
  private PreparedStatement countOfRowsStatement;

  private static final int BUCKET_SIZE = 200000;
  private static final String CDSID_SEPARATOR = "\n";
  private static final String BUCKET_TABLE_NAME = "latest_dataset_representation_revision_buckets";

  private String providerId;
  private String dataSetId;

  public DataCopier(Session session, String provider_id, String dataset_id) {
    this.session = session;
    this.providerId = provider_id;
    this.dataSetId = dataset_id;
    initStatements();
  }

  private void initStatements() {
    selectStatement = session.prepare(
        "SELECT * FROM latest_provider_dataset_rep_rev_replica where provider_id=? and dataset_id=?;");

    insertStatement = session.prepare("INSERT INTO "
        + "latest_dataset_representation_revision_v1 (provider_id, dataset_id, bucket_id, cloud_id, representation_id, revision_timestamp, revision_name, revision_provider, version_id, acceptance, published, mark_deleted) "
        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?);");

    bucketsForSpecificObjectIdStatement = session.prepare(
        "Select bucket_id from latest_dataset_representation_revision_buckets where object_id=?");
    countOfRowsStatement = session.prepare(
        "Select count(*) from latest_dataset_representation_revision_v1 where provider_id=? and dataset_id=? and bucket_id=? and representation_id=? and revision_name=? and revision_provider=? and mark_deleted=? and cloud_id=? ;");

    selectStatement.setConsistencyLevel(ConsistencyLevel.QUORUM);
    insertStatement.setConsistencyLevel(ConsistencyLevel.QUORUM);
    bucketsForSpecificObjectIdStatement.setConsistencyLevel(ConsistencyLevel.QUORUM);
    countOfRowsStatement.setConsistencyLevel(ConsistencyLevel.QUORUM);
  }

  @Override
  public String call() throws Exception {
    BucketsHandler bucketsHandler = new BucketsHandler(session);
    long insertsCounter = 0;
    long readsCounter = 0;
    LOG.info("Starting job for providerId: " + providerId + " and datasetId " + dataSetId + " fthe current progress is: "
        + readsCounter);
    BoundStatement boundStatement = selectStatement.bind(providerId, dataSetId);
    boundStatement.setFetchSize(1000);
    ResultSet rs = session.execute(boundStatement);
    Iterator<Row> ri = rs.iterator();

    try {
      while (hasNextRow(ri)) {
        Row latestProviderDatasetReplica = ri.next();
        if (getRowCount(session, latestProviderDatasetReplica) == 0) {

          Bucket bucket = bucketsHandler.getCurrentBucket(
              BUCKET_TABLE_NAME,
              createProviderDataSetId(latestProviderDatasetReplica.getString("provider_id"),
                  latestProviderDatasetReplica.getString("dataset_id")));

          if (bucket == null || bucket.getRowsCount() >= BUCKET_SIZE) {
            bucket = new Bucket(
                createProviderDataSetId(latestProviderDatasetReplica.getString("provider_id"),
                    latestProviderDatasetReplica.getString("dataset_id")),
                new com.eaio.uuid.UUID().toString(),
                0);
          }
          bucketsHandler.increaseBucketCount(BUCKET_TABLE_NAME, bucket);
          //
          insertIntoNewTable(latestProviderDatasetReplica, bucket.getBucketId());
          //
          if (++insertsCounter % 10000 == 0) {
            LOG.info("Copy table for providerId: " + providerId + " and datasetId " + dataSetId
                + " the current progress for inserts is: " + insertsCounter);
          }
        }

        if (++readsCounter % 10000 == 0) {
          LOG.info(
              "Copy table for providerId: " + providerId + " and datasetId " + dataSetId + " the current progress for reads is: "
                  + readsCounter);

        }
      }
    } catch (Exception e) {
      LOG.error("Migration failed providerId: " + providerId + " and datasetId " + dataSetId + ". Reads: " + readsCounter
          + ". Inserts: " + insertsCounter);
    }
    LOG.info(
        "Finished job for providerId: " + providerId + " and datasetId " + dataSetId + ". Reads: " + readsCounter + ". Inserts: "
            + insertsCounter);
    return "................... The information for providerId: " + providerId + " and datasetId " + dataSetId
        + " is inserted correctly. The total number of inserted rows is:" + readsCounter;
  }

  private void insertIntoNewTable(Row row, String bucketId) {
    BoundStatement insert = insertStatement.bind(
        row.getString(PROVIDER_ID),
        row.getString(DATASET_ID),
        UUID.fromString(bucketId),
        row.getString("cloud_id"),
        row.getString("representation_id"),
        row.getDate("revision_timestamp"),
        row.getString("revision_name"),
        row.getString("revision_provider"),
        row.getUUID("version_id"),
        row.getBool("acceptance"),
        row.getBool("published"),
        row.getBool("mark_deleted")
    );

    int retries = DEFAULT_RETRIES;

    while (true) {
      try {
        session.execute(insert);
        break;
      } catch (Exception e) {
        if (retries-- > 0) {
          System.out.println("Warning while inserting to latest_dataset_representation_revision_v1. Retries left:" + retries);
          try {
            Thread.sleep(SLEEP_TIME);
          } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
            System.err.println(e1.getMessage());
          }
        } else {
          System.err.println("Error while inserting to latest_dataset_representation_revision_v1. " + insert.preparedStatement()
                                                                                                            .getQueryString());
          throw e;
        }
      }
    }
  }


  private long getRowCount(Session session, Row latestProviderDatasetReplica) {

    String objectId = createProviderDataSetId(latestProviderDatasetReplica.getString("provider_id"),
        latestProviderDatasetReplica.getString("dataset_id"));
    BoundStatement boundStatement = bucketsForSpecificObjectIdStatement.bind(objectId);
    ResultSet rs = session.execute(boundStatement);
    Iterator<Row> ri = rs.iterator();

    while (hasNextRow(ri)) {
      UUID bucketId = ri.next().getUUID("bucket_id");
      BoundStatement countBoundStatement = countOfRowsStatement.bind(latestProviderDatasetReplica.getString("provider_id"),
          latestProviderDatasetReplica.getString("dataset_id"),
          bucketId,
          latestProviderDatasetReplica.getString("representation_id"),
          latestProviderDatasetReplica.getString("revision_name"),
          latestProviderDatasetReplica.getString("revision_provider"),
          latestProviderDatasetReplica.getBool("mark_deleted"),
          latestProviderDatasetReplica.getString("cloud_id")

      );
      ResultSet resultSet = session.execute(countBoundStatement);
      long count = resultSet.one().getLong(0);
      if (count > 0) {
        return count;
      }
    }

    return 0;

  }

  private String createProviderDataSetId(String providerId, String dataSetId) {
    return providerId + CDSID_SEPARATOR + dataSetId;
  }
}
