package eu.europeana.cloud.service.mcs.persistent.cassandra;


import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.exceptions.QueryExecutionException;
import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import eu.europeana.cloud.common.model.*;
import eu.europeana.cloud.common.utils.Bucket;
import eu.europeana.cloud.service.commons.utils.BucketsHandler;
import eu.europeana.cloud.service.mcs.exception.DataSetNotExistsException;
import eu.europeana.cloud.service.mcs.exception.ProviderNotExistsException;
import eu.europeana.cloud.service.mcs.exception.RepresentationNotExistsException;
import eu.europeana.cloud.service.mcs.persistent.util.QueryTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.util.*;

import static eu.europeana.cloud.service.mcs.persistent.cassandra.PersistenceUtils.*;

/**
 * Data set repository that uses Cassandra nosql database.
 */
@Repository
public class CassandraDataSetDAO {

    // separator between provider id and dataset id in serialized compund
    // dataset id
    protected static final String CDSID_SEPARATOR = "\n";

    private static final int MAX_PROVIDER_DATASET_BUCKET_COUNT = 210000;

    private static final int MAX_DATASET_ASSIGNMENTS_BUCKET_COUNT = 100000;
    private static final int MAX_DATASET_ASSIGNMENTS_BY_REVISION_ID_BUCKET_COUNT = 250000;
    private static final int MAX_LATEST_DATASET_REPRESENTATION_REVISION_BUCKET_COUNT = 200000;

    private static final String DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS = "data_set_assignments_by_data_set_buckets";

    private static final String DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS = "data_set_assignments_by_revision_id_buckets";

    private static final String LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS = "latest_dataset_representation_revision_buckets";
    private static final String PROVIDER_DATASET_ID = "provider_dataset_id";

    @Autowired
    @Qualifier("dbService")
    private CassandraConnectionProvider connectionProvider;

    @Autowired
    private BucketsHandler bucketsHandler;

    private PreparedStatement createDataSetStatement;

    private PreparedStatement deleteDataSetStatement;

    private PreparedStatement addAssignmentStatement;

    private PreparedStatement addAssignmentByRepresentationStatement;

    private PreparedStatement removeAssignmentByRepresentationsStatement;

    private PreparedStatement removeAssignmentStatement;

    private PreparedStatement listDataSetRepresentationsStatement;

    private PreparedStatement listDataSetsStatement;

    private PreparedStatement getDataSetStatement;

    private PreparedStatement getDataSetsForRepresentationVersionStatement;

    private PreparedStatement getDataSetsRepresentationsNamesList;

    private PreparedStatement addDataSetsRepresentationName;

    private PreparedStatement removeDataSetsRepresentationName;

    private PreparedStatement removeDataSetsAllRepresentationsNames;

    private PreparedStatement hasProvidedRepresentationName;

    private PreparedStatement addDataSetsRevision;

    private PreparedStatement getDataSetsRevision;

    private PreparedStatement removeDataSetsRevision;

    private PreparedStatement getDataSetCloudIdsByRepresentationPublished;

    private PreparedStatement getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentation;

    private PreparedStatement getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentationWhenMarkedDeleted;

    private PreparedStatement getLatestDataSetRepresentationRevision;

    private PreparedStatement insertProviderDatasetRepresentationInfo;

    private PreparedStatement insertLatestProviderDatasetRepresentationInfo;

    private PreparedStatement deleteLatestProviderDatasetRepresentationInfo;

    private PreparedStatement selectFromLatestProviderDatasetRepresentationInfo;

    private PreparedStatement deleteProviderDatasetRepresentationInfo;

    private PreparedStatement listDataSetCloudIdsByRepresentationNoPaging;

    private PreparedStatement getNextProviderDatasetBucket;

    private PreparedStatement getFirstProviderDatasetBucket;

    private PreparedStatement deleteProviderDatasetBuckets;

    private PreparedStatement getProviderDatasetBucketCount;

    private PreparedStatement updateProviderDatasetBuckets;

    private PreparedStatement decreaseProviderDatasetBuckets;

    private PreparedStatement addLatestRevisionForDatasetAssignment;

    private PreparedStatement removeLatestRevisionForDatasetAssignment;

    private PreparedStatement getLatestRevisionForDatasetAssignment;

    @PostConstruct
    private void prepareStatements() {
        createDataSetStatement = connectionProvider
                .getSession()
                .prepare( //
                        "INSERT INTO " //
                                + "data_sets(provider_id, dataset_id, description, creation_date) " //
                                + "VALUES (?,?,?,?);");
        createDataSetStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());

        deleteDataSetStatement = connectionProvider.getSession().prepare( //
                "DELETE FROM " //
                        + "data_sets " //
                        + "WHERE provider_id = ? AND dataset_id = ?;");
        deleteDataSetStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());

        addAssignmentStatement = connectionProvider
                .getSession()
                .prepare( //
                        "INSERT INTO " //
                                + "data_set_assignments_by_data_set (provider_dataset_id, bucket_id, schema_id, cloud_id, version_id, creation_date) " //
                                + "VALUES (?,?,?,?,?,?);");
        addAssignmentStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());


        addAssignmentByRepresentationStatement = connectionProvider
                .getSession()
                .prepare( //
                        "INSERT INTO " //
                                + "data_set_assignments_by_representations (cloud_id, schema_id, version_id, provider_dataset_id, creation_date) " //
                                + "VALUES (?,?,?,?,?);");
        addAssignmentByRepresentationStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());

        removeAssignmentStatement = connectionProvider
                .getSession()
                .prepare( //
                        "DELETE FROM " //
                                + "data_set_assignments_by_data_set " //
                                + "WHERE provider_dataset_id = ? AND bucket_id = ? AND schema_id = ? AND cloud_id = ? AND version_id = ? IF EXISTS;");
        removeAssignmentStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());


        removeAssignmentByRepresentationsStatement = connectionProvider
                .getSession()
                .prepare( //
                        "DELETE FROM " //
                                + "data_set_assignments_by_representations " //
                                + "WHERE cloud_id = ? AND schema_id = ? AND version_id = ? AND provider_dataset_id = ? IF EXISTS;");
        removeAssignmentByRepresentationsStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());


        listDataSetRepresentationsStatement = connectionProvider
                .getSession()
                .prepare( //
                        "SELECT " //
                                + "cloud_id, schema_id, version_id  " //
                                + "FROM data_set_assignments_by_data_set " //
                                + "WHERE provider_dataset_id = ? AND bucket_id = ? "
                                + "LIMIT ?;");
        listDataSetRepresentationsStatement
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        listDataSetsStatement = connectionProvider.getSession().prepare(//
                "SELECT "//
                        + "provider_id, dataset_id, description "//
                        + "FROM data_sets "//
                        + "WHERE provider_id = ? AND dataset_id >= ? LIMIT ?;");
        listDataSetsStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());

        getDataSetStatement = connectionProvider.getSession().prepare(//
                "SELECT "//
                        + "provider_id, dataset_id, description "//
                        + "FROM data_sets "//
                        + "WHERE provider_id = ? AND dataset_id = ?;");
        getDataSetStatement.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());

        getDataSetsForRepresentationVersionStatement = connectionProvider.getSession()
                .prepare(//
                        "SELECT "//
                                + "provider_dataset_id "//
                                + "FROM data_set_assignments_by_representations "//
                                + "WHERE cloud_id = ? AND schema_id = ? AND version_id= ?;");
        getDataSetsForRepresentationVersionStatement
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());


        getDataSetsRepresentationsNamesList = connectionProvider.getSession()
                .prepare(
                        "SELECT representation_names FROM data_set_representation_names where provider_id = ? and dataset_id = ?;");
        getDataSetsRepresentationsNamesList
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        addDataSetsRepresentationName = connectionProvider.getSession()
                .prepare(
                        "UPDATE data_set_representation_names SET representation_names = representation_names + ? WHERE provider_id = ? and dataset_id = ?");
        addDataSetsRepresentationName
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        removeDataSetsRepresentationName = connectionProvider.getSession()
                .prepare(
                        "UPDATE data_set_representation_names SET representation_names = representation_names - ? WHERE provider_id = ? and dataset_id = ?;");
        removeDataSetsRepresentationName
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        removeDataSetsAllRepresentationsNames = connectionProvider.getSession()
                .prepare(
                        "DELETE FROM data_set_representation_names where provider_id = ? and dataset_id = ?;");
        removeDataSetsAllRepresentationsNames
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        hasProvidedRepresentationName = connectionProvider.getSession()
                .prepare(
                        "SELECT " //
                                + "schema_id, cloud_id " //
                                + "FROM data_set_assignments_by_data_set " //
                                + "WHERE provider_dataset_id = ? AND bucket_id = ? AND schema_id = ? LIMIT 1;");
        hasProvidedRepresentationName
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        addDataSetsRevision = connectionProvider
                .getSession()
                .prepare( //
                        "INSERT INTO " //
                                + "data_set_assignments_by_revision_id_v1 (provider_id, dataset_id, bucket_id, revision_provider_id, revision_name, revision_timestamp, representation_id, cloud_id, published, acceptance, mark_deleted) " //
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,?);");
        addDataSetsRevision.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());

        removeDataSetsRevision
                = connectionProvider.getSession().prepare(//
                "DELETE "//
                        + "FROM data_set_assignments_by_revision_id_v1 "//
                        + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND revision_provider_id = ? AND revision_name = ? AND revision_timestamp = ? AND representation_id = ? " +
                        "AND cloud_id = ? IF EXISTS;");
        removeDataSetsRevision
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        getDataSetsRevision = connectionProvider.getSession().prepare(//
                "SELECT "//
                        + "cloud_id, published, acceptance, mark_deleted "//
                        + "FROM data_set_assignments_by_revision_id_v1 "//
                        + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND revision_provider_id = ? AND revision_name = ? AND revision_timestamp = ? AND representation_id = ? LIMIT ?;");
        getDataSetsRevision
                .setConsistencyLevel(connectionProvider.getConsistencyLevel());

        listDataSetCloudIdsByRepresentationNoPaging = connectionProvider.getSession()
                .prepare( //
                        "SELECT " //
                                + "representation_id, revision_timestamp, revision_id, cloud_id " //
                                + "FROM provider_dataset_representation " //
                                + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ?;");
        listDataSetCloudIdsByRepresentationNoPaging.setConsistencyLevel(connectionProvider
                .getConsistencyLevel());

        getDataSetCloudIdsByRepresentationPublished = connectionProvider.getSession().prepare("SELECT " //
                + "cloud_id, version_id, revision_id, published, mark_deleted, acceptance " //
                + "FROM provider_dataset_representation " //
                + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND representation_id = ? AND revision_timestamp > ? LIMIT ?;");
        getDataSetCloudIdsByRepresentationPublished.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentation = connectionProvider.getSession().prepare("SELECT cloud_id, revision_timestamp " //
                + "FROM latest_dataset_representation_revision_v1 " //
                + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND revision_name = ? And revision_provider = ? And representation_id = ? AND mark_deleted = false AND cloud_id > ? LIMIT ? ;");
        getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentation.setConsistencyLevel(connectionProvider.getConsistencyLevel());


        getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentationWhenMarkedDeleted = connectionProvider.getSession().prepare("SELECT cloud_id, revision_timestamp " //
                + "FROM latest_dataset_representation_revision_v1 " //
                + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND revision_name = ? And revision_provider = ? And representation_id = ? AND mark_deleted = ? AND cloud_id > ? LIMIT ? ;");
        getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentationWhenMarkedDeleted.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        getLatestDataSetRepresentationRevision = connectionProvider.getSession().prepare("SELECT * " //
                + "FROM latest_dataset_representation_revision_v1 " //
                + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND representation_id = ? And revision_name = ? and revision_provider = ? AND mark_deleted = ? AND cloud_id = ?;");
        getLatestDataSetRepresentationRevision.setConsistencyLevel(connectionProvider.getConsistencyLevel());


        insertProviderDatasetRepresentationInfo = connectionProvider.getSession().prepare("INSERT INTO " //
                + "provider_dataset_representation (provider_id, dataset_id, bucket_id, cloud_id, version_id, representation_id," //
                + "revision_id, revision_timestamp, acceptance, published, mark_deleted) " //
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?);");
        insertProviderDatasetRepresentationInfo.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        insertLatestProviderDatasetRepresentationInfo = connectionProvider.getSession().prepare("INSERT INTO " //
                + "latest_dataset_representation_revision_v1 (provider_id, dataset_id,bucket_id,cloud_id,representation_id," //
                + "revision_name,revision_provider, revision_timestamp,version_id, acceptance, published, mark_deleted) " //
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?);");
        insertLatestProviderDatasetRepresentationInfo.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        deleteLatestProviderDatasetRepresentationInfo = connectionProvider.getSession().prepare("DELETE FROM " //
                + "latest_dataset_representation_revision_v1 WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND representation_id =? AND revision_name =? And revision_provider =? and mark_deleted = ? and cloud_id = ? IF EXISTS");
        deleteLatestProviderDatasetRepresentationInfo.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        selectFromLatestProviderDatasetRepresentationInfo = connectionProvider.getSession().prepare("select version_id,revision_timestamp,cloud_id FROM " //
                + "latest_dataset_representation_revision_v1 WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND representation_id =? And revision_name =?" +
                "And revision_provider=? AND mark_deleted = ? AND cloud_id = ? ");
        selectFromLatestProviderDatasetRepresentationInfo.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        deleteProviderDatasetRepresentationInfo = connectionProvider.getSession().prepare(//
                "DELETE FROM " //
                        + "provider_dataset_representation " //
                        + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ? AND representation_id = ? AND revision_timestamp = ? AND cloud_id = ?;");
        deleteProviderDatasetRepresentationInfo.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        updateProviderDatasetBuckets = connectionProvider.getSession().prepare("UPDATE datasets_buckets " //
                + "SET rows_count = rows_count + 1 WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ?;");
        updateProviderDatasetBuckets.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        decreaseProviderDatasetBuckets = connectionProvider.getSession().prepare("UPDATE datasets_buckets " //
                + "SET rows_count = rows_count - 1 WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ?;");
        decreaseProviderDatasetBuckets.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        getProviderDatasetBucketCount = connectionProvider.getSession().prepare("SELECT bucket_id, rows_count " //
                + "FROM datasets_buckets " //
                + "WHERE provider_id = ? AND dataset_id = ?;");
        getProviderDatasetBucketCount.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        getNextProviderDatasetBucket = connectionProvider.getSession().prepare("SELECT bucket_id " //
                + "FROM datasets_buckets " //
                + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id > ? LIMIT 1;");
        getNextProviderDatasetBucket.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        getFirstProviderDatasetBucket = connectionProvider.getSession().prepare("SELECT bucket_id " //
                + "FROM datasets_buckets " //
                + "WHERE provider_id = ? AND dataset_id = ? LIMIT 1;");
        getFirstProviderDatasetBucket.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        deleteProviderDatasetBuckets = connectionProvider.getSession().prepare("DELETE FROM " //
                + "datasets_buckets "
                + "WHERE provider_id = ? AND dataset_id = ? AND bucket_id = ?;");
        deleteProviderDatasetBuckets.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        addLatestRevisionForDatasetAssignment = connectionProvider.getSession().prepare(
                "INSERT INTO latest_revisions_for_dataset_assignment(provider_id, dataset_id, representation_id, revision_name, revision_provider_id, revision_timestamp, cloud_id, version_id)\n" +
                        "VALUES(?, ?, ?, ?, ?, ?, ?, ?);"
        );
        addLatestRevisionForDatasetAssignment.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        removeLatestRevisionForDatasetAssignment = connectionProvider.getSession().prepare(
                "DELETE FROM latest_revisions_for_dataset_assignment WHERE provider_id = ? AND dataset_id = ? AND representation_id = ? AND cloud_id = ? AND revision_name = ? AND revision_provider_id = ?;"
        );
        removeLatestRevisionForDatasetAssignment.setConsistencyLevel(connectionProvider.getConsistencyLevel());

        getLatestRevisionForDatasetAssignment = connectionProvider.getSession().prepare(
                "SELECT revision_timestamp, version_id FROM latest_revisions_for_dataset_assignment WHERE provider_id =? AND dataset_id =? AND representation_id = ? AND cloud_id = ? AND revision_name = ? AND revision_provider_id = ?;"
        );
        getLatestRevisionForDatasetAssignment.setConsistencyLevel(connectionProvider.getConsistencyLevel());
    }

    /**
     * Returns stubs of representations assigned to a data set. Stubs contain
     * cloud id and schema of the representation, may also contain version (if a
     * certain version is in a data set).
     *
     * @param providerId data set owner's (provider's) id
     * @param dataSetId  data set id
     * @param nextToken  next token containing information about paging state and bucket id
     * @param limit      maximum size of returned list
     * @return
     */
    public List<Properties> listDataSet(String providerId, String dataSetId, String nextToken, int limit)
            throws NoHostAvailableException, QueryExecutionException {
        String providerDataSetId = createProviderDataSetId(providerId, dataSetId);
        List<Properties> representationStubs = new ArrayList<>();

        Bucket bucket = null;
        PagingState state;

        if (nextToken == null) {
            // there is no next token so do not set paging state, take the first bucket for provider's dataset
            bucket = bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, providerDataSetId);
            state = null;
        } else {
            // next token is set, parse it to retrieve paging state and bucket id (token is concatenation of paging state and bucket id using _ character
            String[] parts = nextToken.split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException("nextToken format is wrong. nextToken = " + nextToken);
            }

            // first element is the paging state
            state = getPagingState(parts[0]);
            // second element is bucket id
            bucket = getAssignmentBucketId(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, parts[1], state, providerDataSetId);
        }

        // if the bucket is null it means we reached the end of data
        if (bucket == null) {
            return representationStubs;
        }

        // bind parameters, set limit to max int value
        BoundStatement boundStatement = listDataSetRepresentationsStatement.bind(providerDataSetId, UUID.fromString(bucket.getBucketId()), Integer.MAX_VALUE);
        // limit page to "limit" number of results
        boundStatement.setFetchSize(limit);
        // when this is not a first page call set paging state in the statement
        if (state != null) {
            boundStatement.setPagingState(state);
        }

        // execute query
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);

        // get available results
        int available = rs.getAvailableWithoutFetching();
        for (int i = 0; i < available; i++) {
            Row row = rs.one();
            Properties properties = new Properties();
            properties.put("cloudId", row.getString("cloud_id"));
            properties.put("versionId", row.getUUID("version_id").toString());
            properties.put("schema", row.getString("schema_id"));
            representationStubs.add(properties);
        }

        if (representationStubs.size() == limit) {
            // we reached the page limit, prepare the next slice string to be used for the next page
            String nextSlice = getNextSlice(rs.getExecutionInfo().getPagingState(), bucket.getBucketId(), providerId, dataSetId);

            if (nextSlice != null) {
                Properties properties = new Properties();
                properties.put("nextSlice", nextSlice);
                representationStubs.add(properties);
            }
        } else {
            // we reached the end of bucket but number of results is less than the page size - in this case if there are more buckets we should retrieve number of results that will feed the page
            if (bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, providerDataSetId) != null) {
                String nextSlice = "_" + bucket.getBucketId();
                representationStubs.addAll(listDataSet(providerId, dataSetId, nextSlice, limit - representationStubs.size()));
            }
        }

        return representationStubs;
    }

    /**
     * Adds representation to a data set. Might add representation in latest
     * persistent or specified version. Does not do any kind of parameter
     * validation - specified data set and representation version must exist
     * before invoking this method.
     *
     * @param providerId data set owner's (provider's) id
     * @param dataSetId  data set id
     * @param recordId   record id
     * @param schema     representation schema
     * @param version    representation version (might be null if newest version is to
     *                   be assigned)
     */
    public void addAssignment(String providerId, String dataSetId,
                              String recordId, String schema, String version)
            throws NoHostAvailableException, QueryExecutionException {
        Date now = new Date();
        String providerDataSetId = createProviderDataSetId(providerId,
                dataSetId);
        UUID versionId = null;
        if (version != null) {
            versionId = UUID.fromString(version);
        }

        Bucket bucket = bucketsHandler.getCurrentBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, providerDataSetId);
        // when there is no bucket or bucket rows count is max we should add another bucket
        if (bucket == null || bucket.getRowsCount() >= MAX_DATASET_ASSIGNMENTS_BUCKET_COUNT) {
            bucket = new Bucket(providerDataSetId, createBucket(), 0);
        }
        bucketsHandler.increaseBucketCount(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, bucket);

        BoundStatement boundStatement = addAssignmentStatement.bind(
                providerDataSetId, UUID.fromString(bucket.getBucketId()), schema, recordId, versionId, now);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);

        boundStatement = addAssignmentByRepresentationStatement.bind(recordId, schema, versionId, providerDataSetId, now);
        rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
    }

    /**
     * Returns data sets to which representation (in specified or latest
     * version) is assigned to.
     *
     * @param cloudId  record id
     * @param schemaId representation schema
     * @param version  representation version (might be null)
     * @return list of data set ids
     */
    public Collection<CompoundDataSetId> getDataSetAssignments(String cloudId, String schemaId, String version)
            throws NoHostAvailableException, QueryExecutionException {
        BoundStatement boundStatement = getDataSetsForRepresentationVersionStatement.bind(cloudId, schemaId, UUID.fromString(version));
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
        List<CompoundDataSetId> ids = new ArrayList<>();
        for (Row r : rs) {
            String providerDataSetId = r.getString(PROVIDER_DATASET_ID);
            ids.add(createCompoundDataSetId(providerDataSetId));
        }
        return ids;
    }


    /**
     * Returns data sets to which representation in specific version
     * version) is assigned to.
     *
     * @param cloudId  record id
     * @param schemaId representation schema
     * @param version  representation version
     * @return list of data set ids
     */
    public Collection<CompoundDataSetId> getDataSetAssignmentsByRepresentationVersion(String cloudId, String schemaId, String version)
            throws NoHostAvailableException, QueryExecutionException, RepresentationNotExistsException {
        if (version == null) {
            throw new RepresentationNotExistsException();
        }
        return getDataSetAssignments(cloudId,schemaId,version);
    }


    /**
     * Returns data set from specified provider with specified id. Throws
     * exception when provider does not exist. Returns null if provider exists
     * but does not have data set with specified id.
     *
     * @param providerId data set owner's (provider's) id
     * @param dataSetId  data set id
     * @return data set
     */
    public DataSet getDataSet(String providerId, String dataSetId)
            throws NoHostAvailableException, QueryExecutionException {
        BoundStatement boundStatement = getDataSetStatement.bind(providerId,
                dataSetId);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
        Row row = rs.one();
        if (row == null) {
            return null;
        }
        DataSet ds = new DataSet();
        ds.setProviderId(providerId);
        ds.setId(dataSetId);
        ds.setDescription(row.getString("description"));
        return ds;
    }

    /**
     * Removes representation from data set (regardless representation version).
     *
     * @param providerId data set owner's (provider's) id
     * @param dataSetId  data set id
     * @param recordId   record's id
     * @param schema     representation's schema
     */
    public void removeAssignment(String providerId, String dataSetId,
                                 String recordId, String schema, String versionId) throws NoHostAvailableException,
            QueryExecutionException {
        String providerDataSetId = createProviderDataSetId(providerId,
                dataSetId);

        Bucket bucket = bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, providerDataSetId);

        while (bucket != null) {
            BoundStatement boundStatement = removeAssignmentStatement.bind(
                    providerDataSetId, UUID.fromString(bucket.getBucketId()), schema, recordId, UUID.fromString(versionId));
            ResultSet rs = connectionProvider.getSession().execute(boundStatement);
            QueryTracer.logConsistencyLevel(boundStatement, rs);
            if (rs.wasApplied()) {
                // remove bucket count
                bucketsHandler.decreaseBucketCount(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, bucket);
                removeAssignmentByRepresentation(providerDataSetId, recordId, schema, versionId);
                return;
            }
            bucket = bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, providerDataSetId, bucket);
        }
    }

    private void removeAssignmentByRepresentation(String providerDataSetId, String cloudId, String schema, String versionId) {
        BoundStatement boundStatement = removeAssignmentByRepresentationsStatement.bind(cloudId, schema, UUID.fromString(versionId), providerDataSetId);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
    }

    /**
     * Creates or updates data set for a provider.
     *
     * @param providerId   data set owner's (provider's) id
     * @param dataSetId    data set id
     * @param description  description of data set.
     * @param creationTime creation date
     * @return created (or updated) data set.
     */
    public DataSet createDataSet(String providerId, String dataSetId,
                                 String description, Date creationTime)
            throws NoHostAvailableException, QueryExecutionException {
        BoundStatement boundStatement = createDataSetStatement.bind(providerId,
                dataSetId, description, creationTime);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
        DataSet ds = new DataSet();
        ds.setId(dataSetId);
        ds.setDescription(description);
        ds.setProviderId(providerId);
        return ds;
    }

    /**
     * Lists all data sets for a provider.
     *
     * @param providerId         data set owner's (provider's) id
     * @param thresholdDatasetId parameter used to pagination, returned representations wil
     *                           have dataSetId >= thresholdDatasetId. Might be null.
     * @param limit              max size of returned data set list.
     * @return list of data sets.
     */
    public List<DataSet> getDataSets(String providerId, String thresholdDatasetId, int limit)
            throws NoHostAvailableException, QueryExecutionException {

        BoundStatement boundStatement = listDataSetsStatement.bind(providerId,
                thresholdDatasetId != null ? thresholdDatasetId : "", limit);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);

        List<DataSet> result = new ArrayList<>(limit);
        for (Row row : rs) {
            DataSet ds = new DataSet();
            ds.setProviderId(providerId);
            ds.setId(row.getString("dataset_id"));
            ds.setDescription(row.getString("description"));
            result.add(ds);
        }

        return result;
    }

    /**
     * Deletes data set with all its assignments.
     *
     * @param providerId data set owner's (provider's) id
     * @param dataSetId  data set id
     */
    public void deleteDataSet(String providerId, String dataSetId)
            throws NoHostAvailableException, QueryExecutionException {
        removeAllDataSetCloudIdsByRepresentation(providerId, dataSetId);
        removeAllDataSetBuckets(providerId, dataSetId);
        // remove dataset itself
        BoundStatement boundStatement = deleteDataSetStatement.bind(providerId, dataSetId);
        connectionProvider.getSession().execute(boundStatement);
    }

    private void removeAllDataSetBuckets(String providerId, String dataSetId) {
        for (String bucket_id : getAllDatasetBuckets(providerId, dataSetId)) {
            connectionProvider.getSession().execute(
                    deleteProviderDatasetBuckets.bind(providerId, dataSetId, UUID.fromString(bucket_id)));
        }
    }

    private List<String> getAllDatasetBuckets(String providerId, String dataSetId) {
        List<String> result = new ArrayList<>();
        BoundStatement boundStatement = getProviderDatasetBucketCount.bind(providerId, dataSetId);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);

        for (Row row : rs) {
            result.add(row.getUUID("bucket_id").toString());
        }
        return result;
    }

    private void removeAllDataSetCloudIdsByRepresentation(String providerId, String dataSetId) {
        for (String bucket_id : getAllDatasetBuckets(providerId, dataSetId)) {
            removeAllDataSetCloudIdsByRepresentationBucket(providerId, dataSetId, bucket_id);
        }
    }

    private void removeAllDataSetCloudIdsByRepresentationBucket(String providerId, String dataSetId, String bucket_id) {
        BoundStatement boundStatement = listDataSetCloudIdsByRepresentationNoPaging
                .bind(providerId, dataSetId, UUID.fromString(bucket_id));
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
        for (Row row : rs) {
            String cloudId = row.getString("cloud_id");
            String schemaId = row.getString("representation_id");
            Date revisionTimestamp = row.getDate("revision_timestamp");
            connectionProvider.getSession().execute(
                    deleteProviderDatasetRepresentationInfo.bind(providerId, dataSetId, UUID.fromString(bucket_id), schemaId, revisionTimestamp, cloudId));
            decreaseProviderDatasetBuckets(providerId, dataSetId, bucket_id);
        }
    }

    public Set<String> getAllRepresentationsNamesForDataSet(String providerId, String dataSetId) {
        BoundStatement boundStatement = getDataSetsRepresentationsNamesList.bind(providerId, dataSetId);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
        Row row = rs.one();
        if (row == null) {
            return Collections.emptySet();
        } else {
            return row.getSet("representation_names", String.class);
        }
    }

    public void addDataSetsRepresentationName(String providerId, String dataSetId, String representationName) {
        Set<String> sample = new HashSet<>();
        sample.add(representationName);
        BoundStatement boundStatement = addDataSetsRepresentationName.bind(sample, providerId, dataSetId);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
    }

    public void removeRepresentationNameForDataSet(String representationName, String providerId, String dataSetId) {
        Set<String> sample = new HashSet<String>();
        sample.add(representationName);
        BoundStatement boundStatement = removeDataSetsRepresentationName.bind(sample, providerId, dataSetId);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
    }

    public void removeAllRepresentationsNamesForDataSet(String providerId, String dataSetId) {
        BoundStatement boundStatement = removeDataSetsAllRepresentationsNames.bind(providerId, dataSetId);
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
    }

    public boolean hasMoreRepresentations(String providerId, String datasetId, String representationName) {
        String providerDatasetId = providerId + CDSID_SEPARATOR + datasetId;

        Bucket bucket = bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, providerDatasetId);

        while (bucket != null) {
            BoundStatement boundStatement = hasProvidedRepresentationName.bind(providerDatasetId, UUID.fromString(bucket.getBucketId()), representationName);
            ResultSet rs = connectionProvider.getSession().execute(boundStatement);
            QueryTracer.logConsistencyLevel(boundStatement, rs);
            if (rs.one() != null) {
                return true;
            }
            bucket = bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, providerDatasetId, bucket);
        }
        return false;
    }

    public void addDataSetsRevision(String providerId, String datasetId, Revision revision, String representationName, String cloudId) {
        //
        String providerDataSetId = createProviderDataSetId(providerId, datasetId);
        Bucket bucket = bucketsHandler.getCurrentBucket(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS, providerDataSetId);
        // when there is no bucket or bucket rows count is max we should add another bucket
        if (bucket == null || bucket.getRowsCount() >= MAX_DATASET_ASSIGNMENTS_BY_REVISION_ID_BUCKET_COUNT) {
            bucket = new Bucket(providerDataSetId, createBucket(), 0);
        }
        bucketsHandler.increaseBucketCount(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS, bucket);
        //
        BoundStatement boundStatement = addDataSetsRevision.bind(providerId, datasetId, UUID.fromString(bucket.getBucketId()), revision.getRevisionProviderId(), revision.getRevisionName(), revision.getCreationTimeStamp(), representationName, cloudId, revision.isPublished(), revision.isAcceptance(), revision.isDeleted());
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);
    }

    public void removeDataSetsRevision(String providerId, String datasetId, Revision revision, String
            representationName, String cloudId) {
        List<Bucket> availableBuckets = bucketsHandler.getAllBuckets(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS,
                createProviderDataSetId(providerId, datasetId));

        for (Bucket bucket : availableBuckets) {
            BoundStatement boundStatement = removeDataSetsRevision.bind(providerId, datasetId, UUID.fromString(bucket.getBucketId()), revision.getRevisionProviderId(), revision.getRevisionName(), revision.getCreationTimeStamp(),
                    representationName, cloudId);
            ResultSet rs = connectionProvider.getSession().execute(boundStatement);
            QueryTracer.logConsistencyLevel(boundStatement, rs);
            if (rs.wasApplied()) {
                bucketsHandler.decreaseBucketCount(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS, bucket);
                return;
            }
        }
    }


    public List<Properties> getDataSetsRevisions(String providerId, String dataSetId, String revisionProviderId, String revisionName, Date revisionTimestamp, String representationName, String nextToken, int limit) {
        String providerDataSetId = createProviderDataSetId(providerId, dataSetId);
        List<Properties> result = new ArrayList<>(limit);

        Bucket bucket = null;
        PagingState state;

        if (nextToken == null) {
            // there is no next token so do not set paging state, take the first bucket for provider's dataset
            bucket = bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS, providerDataSetId);
            state = null;
        } else {
            // next token is set, parse it to retrieve paging state and bucket id (token is concatenation of paging state and bucket id using _ character
            String[] parts = nextToken.split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException("nextToken format is wrong. nextToken = " + nextToken);
            }

            // first element is the paging state
            state = getPagingState(parts[0]);
            // second element is bucket id
            bucket = getAssignmentBucketId(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS, parts[1], state, providerDataSetId);
        }

        // if the bucket is null it means we reached the end of data
        if (bucket == null) {
            return result;
        }

        // bind parameters, set limit to max int value
        BoundStatement boundStatement = getDataSetsRevision.bind(providerId, dataSetId, UUID.fromString(bucket.getBucketId()), revisionProviderId, revisionName, revisionTimestamp, representationName, Integer.MAX_VALUE);
        // limit page to "limit" number of results
        boundStatement.setFetchSize(limit);
        // when this is not a first page call set paging state in the statement
        if (nextToken != null) {
            boundStatement.setPagingState(state);
        }
        // execute query
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        PagingState ps = rs.getExecutionInfo().getPagingState();
        QueryTracer.logConsistencyLevel(boundStatement, rs);

        // get available results
        Iterator<Row> iterator = rs.iterator();
        while(iterator.hasNext()){
            Row row = iterator.next();
            Properties properties = new Properties();
            properties.put("cloudId", row.getString("cloud_id"));
            properties.put("acceptance", Boolean.valueOf(row.getBool("acceptance")).toString());
            properties.put("published", Boolean.valueOf(row.getBool("published")).toString());
            properties.put("deleted", Boolean.valueOf(row.getBool("mark_deleted")).toString());
            result.add(properties);

            if (result.size() >= limit){
                break;
            }
        }

        if (result.size() == limit) {
            if (!rs.isExhausted()) {
                // we reached the page limit, prepare the next slice string to be used for the next page
                Properties properties = new Properties();
                properties.put("nextSlice", ps.toString() + "_" + bucket.getBucketId());
                result.add(properties);
            } else {
                // we reached the end of bucket and limit - in this case if there are more buckets we should set proper nextSlice
                if (bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS, providerDataSetId, bucket) != null) {
                    Properties properties = new Properties();
                    properties.put("nextSlice", "_" + bucket.getBucketId());
                    result.add(properties);
                }
            }
        } else {
            // we reached the end of bucket but number of results is less than the page size - in this case if there are more buckets we should retrieve number of results that will feed the page
            if (bucketsHandler.getNextBucket(DATA_SET_ASSIGNMENTS_BY_REVISION_ID_BUCKETS, providerDataSetId, bucket) != null) {
                String nextSlice = "_" + bucket.getBucketId();
                result.addAll(
                        getDataSetsRevisions(providerId, dataSetId, revisionProviderId, revisionName, revisionTimestamp,
                                representationName, nextSlice, limit - result.size()));
            }
        }

        return result;
    }


    /**
     * Lists cloud identifiers of provider's data set having given representation name and revision published after a specific time. Together with cloud identifier also
     * version identifier and revision identifier are returned. All these values are packed in Properties object where keys are: cloudId, versionId, revisionId. The last element
     * of the list may contain Properties object with just one property (key is nextSlice) indicating token that may be used for next page of results.
     *
     * @param providerId         data set provider id
     * @param dataSetId          identifier of a data set
     * @param representationName representation name
     * @param dateFrom           date of last revision
     * @param nextToken          cloud identifier combined with timestamp from which to start the result list, used in pagination, may be null
     * @param limit              max size of returned cloud identifiers list.
     * @return list of Properties object where each such object contains cloud identifier, version identifier and revision identifier
     */
    public List<Properties> getDataSetCloudIdsByRepresentationPublished(String providerId, String dataSetId, String representationName, Date dateFrom, String nextToken, int limit)
            throws NoHostAvailableException, QueryExecutionException {
        List<Properties> result = new ArrayList<>(limit);

        String bucketId;
        PagingState state;

        if (nextToken == null) {
            // there is no next token so do not set paging state, take the first bucket for provider's dataset
            bucketId = getNextBucket(providerId, dataSetId, null);
            state = null;
        } else {
            // next token is set, parse it to retrieve paging state and bucket id (token is concatenation of paging state and bucket id using _ character
            String[] parts = nextToken.split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException("nextToken format is wrong. nextToken = " + nextToken);
            }

            // first element is the paging state
            state = getPagingState(parts[0]);
            // second element is bucket id
            bucketId = getBucketId(parts[1], state, providerId, dataSetId);
        }

        // if the bucket is null it means we reached the end of data
        if (bucketId == null) {
            return result;
        }

        // bind parameters, set limit to max int value
        BoundStatement boundStatement = getDataSetCloudIdsByRepresentationPublished.bind(providerId, dataSetId, UUID.fromString(bucketId), representationName, dateFrom, Integer.MAX_VALUE);
        // limit page to "limit" number of results
        boundStatement.setFetchSize(limit);
        // when this is not a first page call set paging state in the statement
        if (state != null) {
            boundStatement.setPagingState(state);
        }

        // execute query
        ResultSet rs = connectionProvider.getSession().execute(boundStatement);
        QueryTracer.logConsistencyLevel(boundStatement, rs);

        // get available results
        int available = rs.getAvailableWithoutFetching();
        for (int i = 0; i < available; i++) {
            Row row = rs.one();
            Properties properties = new Properties();
            properties.put("cloudId", row.getString("cloud_id"));
            properties.put("versionId", row.getUUID("version_id").toString());
            properties.put("revisionId", row.getString("revision_id"));
            properties.put("published", row.getBool("published"));
            properties.put("deleted", row.getBool("mark_deleted"));
            properties.put("acceptance", row.getBool("acceptance"));
            result.add(properties);
        }

        if (result.size() == limit) {
            // we reached the page limit, prepare the next slice string to be used for the next page
            String nextSlice = getNextSlice(rs.getExecutionInfo().getPagingState(), bucketId, providerId, dataSetId);

            if (nextSlice != null) {
                Properties properties = new Properties();
                properties.put("nextSlice", nextSlice);
                result.add(properties);
            }
        } else {
            // we reached the end of bucket but number of results is less than the page size - in this case if there are more buckets we should retrieve number of results that will feed the page
            if (getNextBucket(providerId, dataSetId, bucketId) != null) {
                String nextSlice = "_" + bucketId;
                result.addAll(getDataSetCloudIdsByRepresentationPublished(providerId, dataSetId, representationName, dateFrom, nextSlice, limit - result.size()));
            }
        }

        return result;
    }


    /**
     * get a list of the latest cloud identifier,revision timestamp that belong to data set of a specified provider for a specific representation and revision.
     * This list will contain one row per revision per cloudId;
     *
     * @param dataSetId               data set identifier
     * @param providerId              provider identifier
     * @param representationName      representation name
     * @param revisionName            revision name
     * @param revisionProvider        revision provider
     * @param startFrom               cloudId to start from
     * @param isDeleted               revision marked-deleted
     * @param numberOfElementsPerPage number of elements in a slice
     * @return list  of the latest cloud identifier,revision timestamp that belong to data set of a specified provider for a specific representation and revision.
     * This list will contain one row per revision per cloudId ;
     * @throws ProviderNotExistsException
     * @throws DataSetNotExistsException
     */

    public List<CloudIdAndTimestampResponse> getLatestDataSetCloudIdByRepresentationAndRevision(String providerId, String dataSetId, String revisionName, String revisionProvider, String representationName, String startFrom, Boolean isDeleted, int numberOfElementsPerPage)
            throws NoHostAvailableException, QueryExecutionException {
        List<CloudIdAndTimestampResponse> cloudIdAndTimestampResponseList = new ArrayList<>();
        BoundStatement boundStatement = null;
        if (startFrom == null) {
            startFrom = "";
        }

        List<Bucket> buckets = bucketsHandler.getAllBuckets(LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS, createProviderDataSetId(providerId, dataSetId));
        for (Bucket bucket : buckets) {
            if (isDeleted == null) {
                boundStatement = getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentation.bind(providerId, dataSetId, UUID.fromString(bucket.getBucketId()), revisionName, revisionProvider, representationName, startFrom, numberOfElementsPerPage);
            } else
                boundStatement = getLatestDataSetCloudIdsAndTimestampsByRevisionAndRepresentationWhenMarkedDeleted.bind(providerId, dataSetId, UUID.fromString(bucket.getBucketId()), revisionName, revisionProvider, representationName, isDeleted, startFrom, numberOfElementsPerPage);

            ResultSet rs = connectionProvider.getSession().execute(boundStatement);
            QueryTracer.logConsistencyLevel(boundStatement, rs);
            Iterator<Row> iterator = rs.iterator();

            while (iterator.hasNext()) {
                Row row = iterator.next();
                CloudIdAndTimestampResponse cloudIdAndTimestampResponse = new CloudIdAndTimestampResponse(row.getString("cloud_id"), row.getDate("revision_timestamp"));
                cloudIdAndTimestampResponseList.add(cloudIdAndTimestampResponse);
            }
        }
        return cloudIdAndTimestampResponseList;

    }

    /**
     * Get next slice string basing on paging state of the current query and bucket id.
     *
     * @param pagingState paging state of the current query
     * @param bucketId    current bucket identifier
     * @param providerId  provider id needed to retrieve next bucket id
     * @param dataSetId   dataset id needed to retrieve next bucket id
     * @return next slice as the concatenation of paging state and bucket id (paging state may be empty_ or null when there are no more buckets available
     */
    private String getNextSlice(PagingState pagingState, String bucketId, String providerId, String dataSetId) {
        if (pagingState == null) {
            // we possibly reached the end of a bucket, if there are more buckets we should prepare next slice otherwise not
            if (getNextBucket(providerId, dataSetId, bucketId) != null) {
                return "_" + bucketId;
            }
        } else
            return pagingState.toString() + "_" + bucketId;
        return null;
    }

    /**
     * Get bucket id from part of token considering paging state which was retrieved from the same token. This is used for data assignment table where
     * provider id and dataset id are concatenated to one string
     *
     * @param bucketsTableName  table name used for buckets
     * @param tokenPart         part of token containing bucket id
     * @param state             paging state from the same token as the bucket id
     * @param providerDataSetId provider id and dataset id to retrieve next bucket id
     * @return bucket id to be used for the query
     */
    private Bucket getAssignmentBucketId(String bucketsTableName, String tokenPart, PagingState state, String providerDataSetId) {
        if (tokenPart != null && !tokenPart.isEmpty()) {
            // when the state passed in the next token is not null we have to use the same bucket id as the paging state is associated with the query having certain parameter values
            if (state != null) {
                return new Bucket(providerDataSetId, tokenPart, 0);
            }
            // the state part is empty which means we reached the end of the bucket passed in the next token, therefore we need to get the next bucket
            return bucketsHandler.getNextBucket(bucketsTableName, providerDataSetId, new Bucket(providerDataSetId, tokenPart, 0));
        }
        return null;
    }

    /**
     * Get bucket id from part of token considering paging state which was retrieved from the same token
     *
     * @param tokenPart  part of token containing bucket id
     * @param state      paging state from the same token as the bucket id
     * @param providerId provider id to retrieve next bucket id
     * @param dataSetId  dataset id to retrieve next bucket id
     * @return bucket id to be used for the query
     */
    private String getBucketId(String tokenPart, PagingState state, String providerId, String dataSetId) {
        if (tokenPart != null && !tokenPart.isEmpty()) {
            // when the state passed in the next token is not null we have to use the same bucket id as the paging state is associated with the query having certain parameter values
            if (state != null) {
                return tokenPart;
            }
            // the state part is empty which means we reached the end of the bucket passed in the next token, therefore we need to get the next bucket
            return getNextBucket(providerId, dataSetId, tokenPart);
        }
        return null;
    }

    /**
     * Get paging state from part of token. When the token is null or empty paging state is null. Otherwise we can create paging state from that string.
     *
     * @param tokenPart part of token containing string representation of paging state from previous query
     * @return null when token part is empty or null paging state otherwise
     */
    private PagingState getPagingState(String tokenPart) {
        if (tokenPart != null && !tokenPart.isEmpty()) {
            return PagingState.fromString(tokenPart);
        }
        return null;
    }

    private String getNextBucket(String providerId, String dataSetId, String bucketId) {
        BoundStatement bs = bucketId == null ?
                getFirstProviderDatasetBucket.bind(providerId, dataSetId)
                : getNextProviderDatasetBucket.bind(providerId, dataSetId, UUID.fromString(bucketId));
        ResultSet rs = connectionProvider.getSession().execute(bs);
        QueryTracer.logConsistencyLevel(bs, rs);
        Row row = rs.one();
        // there should be only one row or none
        if (row != null) {
            return row.getUUID("bucket_id").toString();
        }
        return null;
    }

    /**
     * Insert row to provider_dataset_representation table.
     *
     * @param dataSetId         data set identifier
     * @param dataSetProviderId provider identifier
     * @param globalId          cloud identifier
     * @param schema            representation name
     * @param revisionId        revision identifier
     * @param timeStamp         revision timestamp
     * @param acceptance        acceptance tag
     * @param published         published tag
     * @param deleted           mark deleted tag
     */
    public void insertProviderDatasetRepresentationInfo(String dataSetId, String dataSetProviderId, String globalId,
                                                        String versionId, String schema, String revisionId, Date timeStamp,
                                                        boolean acceptance, boolean published, boolean deleted)
            throws NoHostAvailableException, QueryExecutionException {
        if (!published) {
            deleteProviderDatasetRepresentationInfo(dataSetId, dataSetProviderId, globalId, schema, timeStamp);
            return;
        }
        String bucketId = null;
        Bucket bucketCount = getCurrentProviderDatasetBucket(dataSetProviderId, dataSetId);
        // when there is no bucket or bucket rows count is max we should add another bucket
        if (bucketCount == null || bucketCount.getRowsCount() >= MAX_PROVIDER_DATASET_BUCKET_COUNT) {
            bucketId = createBucket();
        } else
            bucketId = bucketCount.getBucketId();
        increaseBucketCount(dataSetProviderId, dataSetId, bucketId);

        BoundStatement bs = insertProviderDatasetRepresentationInfo.bind(dataSetProviderId, dataSetId, UUID.fromString(bucketId), globalId, UUID.fromString(versionId), schema,
                revisionId, timeStamp, acceptance, published, deleted);
        ResultSet rs = connectionProvider.getSession().execute(bs);
        QueryTracer.logConsistencyLevel(bs, rs);
    }


    private void increaseBucketCount(String dataSetProviderId, String dataSetId, String bucketId) {
        BoundStatement statement = updateProviderDatasetBuckets.bind(dataSetProviderId, dataSetId, UUID.fromString(bucketId));
        ResultSet rs = connectionProvider.getSession().execute(statement);
        QueryTracer.logConsistencyLevel(statement, rs);
    }


    private String createBucket() {
        return new com.eaio.uuid.UUID().toString();
    }


    public Bucket getCurrentDataSetAssignmentBucket(String providerId, String datasetId) {
        return bucketsHandler.getCurrentBucket(DATA_SET_ASSIGNMENTS_BY_DATA_SET_BUCKETS, createProviderDataSetId(providerId, datasetId));
    }


    public Bucket getCurrentProviderDatasetBucket(String providerId, String datasetId) {
        BoundStatement rowsStatement = getProviderDatasetBucketCount.bind(providerId, datasetId);
        ResultSet rs = connectionProvider.getSession().execute(rowsStatement);
        // get last element on the list
        List<Row> rows = rs.all();
        Row row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (row != null) {
            // TODO Bucket needs refactoring in order to support multi key tables
            return new Bucket(row.getUUID("bucket_id").toString(), row.getLong("rows_count"));
        }
        return null;
    }

    /**
     * Remove row from provider_dataset_representation table.
     *
     * @param dataSetId         data set identifier
     * @param dataSetProviderId provider identifier
     * @param globalId          cloud identifier
     * @param schema            representation name
     * @param timeStamp         revision timestamp
     *                          +
     */
    public void deleteProviderDatasetRepresentationInfo(String dataSetId, String dataSetProviderId, String globalId,
                                                        String schema, Date timeStamp)
            throws NoHostAvailableException, QueryExecutionException {
        String bucketId = getNextBucket(dataSetProviderId, dataSetId, null);
        while (bucketId != null) {
            BoundStatement bs = deleteProviderDatasetRepresentationInfo.bind(dataSetProviderId, dataSetId, UUID.fromString(bucketId), schema,
                    timeStamp, globalId);
            ResultSet rs = connectionProvider.getSession().execute(bs);
            QueryTracer.logConsistencyLevel(bs, rs);
            if (rs.wasApplied()) {
                decreaseProviderDatasetBuckets(dataSetProviderId, dataSetId, bucketId);
                return;
            }
            bucketId = getNextBucket(dataSetProviderId, dataSetId, bucketId);
        }
    }

    private void decreaseProviderDatasetBuckets(String dataSetProviderId, String dataSetId, String bucketId) {
        BoundStatement bs = decreaseProviderDatasetBuckets.bind(dataSetProviderId, dataSetId, UUID.fromString(bucketId));
        ResultSet rs = connectionProvider.getSession().execute(bs);
        QueryTracer.logConsistencyLevel(bs, rs);
    }


    /**
     * Insert row to provider_dataset_representation table.
     *
     * @param dataSetId         data set identifier
     * @param dataSetProviderId provider identifier
     * @param cloudId           cloud identifier
     * @param schema            representation name
     * @param timestamp         timestamp of revision update
     * @param versionId         version identifier
     * @param acceptance        acceptance tag
     * @param published         published tag
     * @param deleted           mark deleted tag
     */
    public void insertLatestProviderDatasetRepresentationInfo(String dataSetId, String dataSetProviderId, String cloudId,
                                                              String schema, String revisionName, String revisionProvider, Date timestamp, String versionId,
                                                              boolean acceptance, boolean published, boolean deleted)
            throws NoHostAvailableException, QueryExecutionException {

        List<Bucket> buckets = bucketsHandler.getAllBuckets(LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS, createProviderDataSetId(dataSetProviderId, dataSetId));
        for (Bucket bucket : buckets) {
            BoundStatement deleteStatement = deleteLatestProviderDatasetRepresentationInfo.bind(
                    dataSetProviderId,
                    dataSetId,
                    UUID.fromString(bucket.getBucketId()),
                    schema,
                    revisionName,
                    revisionProvider,
                    !deleted,
                    cloudId);

            ResultSet rs = connectionProvider.getSession().execute(deleteStatement);

            if (rs.wasApplied()) {
                insertRow(dataSetProviderId,
                        dataSetId,
                        bucket,
                        cloudId,
                        schema,
                        revisionName,
                        revisionProvider,
                        timestamp,
                        versionId,
                        acceptance,
                        published,
                        deleted);
                return;
            }

            deleteStatement = deleteLatestProviderDatasetRepresentationInfo.bind(
                    dataSetProviderId,
                    dataSetId,
                    UUID.fromString(bucket.getBucketId()),
                    schema,
                    revisionName,
                    revisionProvider,
                    deleted,
                    cloudId);

            ResultSet rs1 = connectionProvider.getSession().execute(deleteStatement);
            if (rs1.wasApplied()) {
                insertRow(dataSetProviderId,
                        dataSetId,
                        bucket,
                        cloudId,
                        schema,
                        revisionName,
                        revisionProvider,
                        timestamp,
                        versionId,
                        acceptance,
                        published,
                        deleted);
                return;
            }
        }

        Bucket newBucket = bucketsHandler.getCurrentBucket(LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS, createProviderDataSetId(dataSetProviderId, dataSetId));
        if (newBucket == null || newBucket.getRowsCount() >= MAX_LATEST_DATASET_REPRESENTATION_REVISION_BUCKET_COUNT) {
            newBucket = new Bucket(createProviderDataSetId(dataSetProviderId, dataSetId), createBucket(), 0);
        }

        bucketsHandler.increaseBucketCount(LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS, newBucket);

        BoundStatement insertStatement = insertLatestProviderDatasetRepresentationInfo.bind(
                dataSetProviderId,
                dataSetId,
                UUID.fromString(newBucket.getBucketId()),
                cloudId,
                schema,
                revisionName,
                revisionProvider,
                timestamp,
                UUID.fromString(versionId),
                acceptance,
                published,
                deleted);
        connectionProvider.getSession().execute(insertStatement);
    }

    private void insertRow(String dataSetProviderId, String dataSetId, Bucket bucket, String cloudId,
                           String schema, String revisionName, String revisionProvider, Date timestamp, String versionId,
                           boolean acceptance, boolean published, boolean deleted) {

        BoundStatement insertStatement = insertLatestProviderDatasetRepresentationInfo.bind(
                dataSetProviderId,
                dataSetId,
                UUID.fromString(bucket.getBucketId()),
                cloudId,
                schema,
                revisionName,
                revisionProvider,
                timestamp,
                UUID.fromString(versionId),
                acceptance,
                published,
                deleted);
        connectionProvider.getSession().execute(insertStatement);
    }

    /**
     * Remove row from latest_provider_dataset_representation_revision table.
     *
     * @param dataSetId         data set identifier
     * @param dataSetProviderId provider identifier
     * @param globalId          cloud identifier
     * @param schema            representation name
     *                          +
     */
    public void deleteLatestProviderDatasetRepresentationInfo(String dataSetId, String dataSetProviderId, String globalId,
                                                              String schema, String revisionName, String revisionProvider, boolean markDeleted)
            throws NoHostAvailableException, QueryExecutionException {


        List<Bucket> buckets = bucketsHandler.getAllBuckets(LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS, createProviderDataSetId(dataSetProviderId, dataSetId));
        for (Bucket bucket : buckets) {
            BoundStatement bs = deleteLatestProviderDatasetRepresentationInfo.bind(
                    dataSetProviderId, dataSetId, UUID.fromString(bucket.getBucketId()), schema, revisionName, revisionProvider, markDeleted,
                    globalId);
            ResultSet rs = connectionProvider.getSession().execute(bs);
            QueryTracer.logConsistencyLevel(bs, rs);
            if (rs.wasApplied())
                bucketsHandler.decreaseBucketCount(LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS, bucket);
            return;
        }
    }

    public void addLatestRevisionForDatasetAssignment(DataSet dataSet, Representation representation, Revision
            revision) {

        BoundStatement bs = addLatestRevisionForDatasetAssignment.bind(
                dataSet.getProviderId(),
                dataSet.getId(),
                representation.getRepresentationName(),
                revision.getRevisionName(),
                revision.getRevisionProviderId(),
                revision.getCreationTimeStamp(),
                representation.getCloudId(),
                UUID.fromString(representation.getVersion())
        );
        connectionProvider.getSession().execute(bs);
        ResultSet rs = connectionProvider.getSession().execute(bs);
        QueryTracer.logConsistencyLevel(bs, rs);
    }

    /**
     * get Version from latest_provider_dataset_representation_revision table.
     *
     * @param dataSetId         data set identifier
     * @param dataSetProviderId provider identifier
     * @param globalId          cloud identifier
     * @param schema            representation name
     *                          +
     */

    public Date getLatestRevisionTimeStamp(String dataSetId, String dataSetProviderId, String schema, String revisionName, String revisionProvider, boolean mark_deleted, String globalId
    )
            throws NoHostAvailableException, QueryExecutionException {

        List<Bucket> buckets = bucketsHandler.getAllBuckets(LATEST_DATASET_REPRESENTATION_REVISION_BUCKETS, dataSetProviderId);
        for (Bucket bucket : buckets) {
            BoundStatement bs = selectFromLatestProviderDatasetRepresentationInfo.bind(dataSetProviderId, dataSetId, bucket.getBucketId(), schema, revisionName, revisionProvider,
                    mark_deleted, globalId);
            ResultSet rs = connectionProvider.getSession().execute(bs);
            QueryTracer.logConsistencyLevel(bs, rs);

            if (rs.getAvailableWithoutFetching() == 0) {
                continue;
            }
            return rs.one().getDate("revision_timestamp");
        }
            return null;
    }


    public void removeLatestRevisionForDatasetAssignment(DataSet dataSet, Representation representation, Revision revision) {
        BoundStatement bs = removeLatestRevisionForDatasetAssignment.bind(
                dataSet.getProviderId(),
                dataSet.getId(),
                representation.getRepresentationName(),
                representation.getCloudId(),
                revision.getRevisionName(),
                revision.getRevisionProviderId()
        );
        ResultSet rs = connectionProvider.getSession().execute(bs);
        QueryTracer.logConsistencyLevel(bs, rs);
    }

    public void removeLatestRevisionForDatasetAssignment(DataSet dataSet, Representation representation, String revisionProvider, String revisionName) {
        BoundStatement bs = removeLatestRevisionForDatasetAssignment.bind(
                dataSet.getProviderId(),
                dataSet.getId(),
                representation.getRepresentationName(),
                representation.getCloudId(),
                revisionName,
                revisionProvider
        );
        ResultSet rs = connectionProvider.getSession().execute(bs);
        QueryTracer.logConsistencyLevel(bs, rs);
    }

    public DataSetRepresentationForLatestRevision getRepresentationForLatestRevisionFromDataset(DataSet dataSet, Representation representation, Revision revision) {

        BoundStatement bs = getLatestRevisionForDatasetAssignment.bind(
                dataSet.getProviderId(),
                dataSet.getId(),
                representation.getRepresentationName(),
                representation.getCloudId(),
                revision.getRevisionName(),
                revision.getRevisionProviderId()
        );
        connectionProvider.getSession().execute(bs);
        ResultSet rs = connectionProvider.getSession().execute(bs);
        QueryTracer.logConsistencyLevel(bs, rs);
        Row row = rs.one();

        if (row != null) {
            DataSetRepresentationForLatestRevision result = new DataSetRepresentationForLatestRevision();
            //
            Revision rev = new Revision(revision);
            rev.setCreationTimeStamp(row.getDate("revision_timestamp"));
            result.setRevision(rev);
            //
            Representation rep = new Representation(representation);
            rep.setVersion(row.getUUID("version_id").toString());
            result.setRepresentation(rep);
            return result;
        } else
            return null;
    }

}
