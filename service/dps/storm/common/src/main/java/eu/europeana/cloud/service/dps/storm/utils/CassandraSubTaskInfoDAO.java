package eu.europeana.cloud.service.dps.storm.utils;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.exceptions.QueryExecutionException;
import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import eu.europeana.cloud.common.model.dps.RecordState;
import eu.europeana.cloud.common.model.dps.SubTaskInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The {@link eu.europeana.cloud.common.model.dps.SubTaskInfo} DAO
 *
 * @author akrystian
 */
public class CassandraSubTaskInfoDAO extends CassandraDAO {
    private PreparedStatement subtaskSearchStatement;
    private PreparedStatement subtaskInsertStatement;
    private PreparedStatement processedFilesCountStatement;
    private PreparedStatement removeNotificationsByTaskId;

    private static CassandraSubTaskInfoDAO instance = null;

    public static synchronized CassandraSubTaskInfoDAO getInstance(CassandraConnectionProvider cassandra) {
        if (instance == null) {
            instance = new CassandraSubTaskInfoDAO(cassandra);
        }
        return instance;
    }

    /**
     * @param dbService The service exposing the connection and session
     */
    public CassandraSubTaskInfoDAO(CassandraConnectionProvider dbService) {
        super(dbService);
    }

    @Override
    void prepareStatements() {
        subtaskInsertStatement = dbService.getSession().prepare("INSERT INTO " + CassandraTablesAndColumnsNames.NOTIFICATIONS_TABLE + "("
                + CassandraTablesAndColumnsNames.NOTIFICATION_RESOURCE_NUM
                + "," + CassandraTablesAndColumnsNames.NOTIFICATION_TASK_ID
                + "," + CassandraTablesAndColumnsNames.NOTIFICATION_TOPOLOGY_NAME
                + "," + CassandraTablesAndColumnsNames.NOTIFICATION_RESOURCE
                + "," + CassandraTablesAndColumnsNames.NOTIFICATION_STATE
                + "," + CassandraTablesAndColumnsNames.NOTIFICATION_INFO_TEXT
                + "," + CassandraTablesAndColumnsNames.NOTIFICATION_ADDITIONAL_INFORMATIONS
                + "," + CassandraTablesAndColumnsNames.NOTIFICATION_RESULT_RESOURCE
                + ") VALUES (?,?,?,?,?,?,?,?)");
        subtaskInsertStatement.setConsistencyLevel(dbService.getConsistencyLevel());
        subtaskSearchStatement = dbService.getSession().prepare(
                "SELECT * FROM " + CassandraTablesAndColumnsNames.NOTIFICATIONS_TABLE + " WHERE " + CassandraTablesAndColumnsNames.NOTIFICATION_TASK_ID + " = ?");
        subtaskSearchStatement.setConsistencyLevel(dbService.getConsistencyLevel());


        processedFilesCountStatement = dbService.getSession().prepare(
                "SELECT resource_num FROM " + CassandraTablesAndColumnsNames.NOTIFICATIONS_TABLE + " WHERE " + CassandraTablesAndColumnsNames.NOTIFICATION_TASK_ID + " = ? limit 1");
        processedFilesCountStatement.setConsistencyLevel(dbService.getConsistencyLevel());

        removeNotificationsByTaskId = dbService.getSession().prepare("delete from " + CassandraTablesAndColumnsNames.NOTIFICATIONS_TABLE + " WHERE " + CassandraTablesAndColumnsNames.NOTIFICATION_TASK_ID + " = ?");

    }

    public void insert(int resourceNum, long taskId, String topologyName, String resource, String state, String infoTxt, String additionalInformations, String resultResource)
            throws NoHostAvailableException, QueryExecutionException {
        dbService.getSession().execute(subtaskInsertStatement.bind(resourceNum, taskId, topologyName, resource, state, infoTxt, additionalInformations, resultResource));
    }

    public List<SubTaskInfo> searchById(long taskId)
            throws NoHostAvailableException, QueryExecutionException {
        ResultSet rs = dbService.getSession().execute(subtaskSearchStatement.bind(taskId));
        List<SubTaskInfo> result = new ArrayList<>();
        for (Row row : rs.all()) {
            result.add(new SubTaskInfo(
                    row.getInt(CassandraTablesAndColumnsNames.NOTIFICATION_RESOURCE_NUM),
                    row.getString(CassandraTablesAndColumnsNames.NOTIFICATION_RESOURCE),
                    RecordState.valueOf(row.getString(CassandraTablesAndColumnsNames.NOTIFICATION_STATE)),
                    row.getString(CassandraTablesAndColumnsNames.NOTIFICATION_INFO_TEXT),
                    row.getString(CassandraTablesAndColumnsNames.NOTIFICATION_ADDITIONAL_INFORMATIONS),
                    row.getString(CassandraTablesAndColumnsNames.NOTIFICATION_RESULT_RESOURCE)
            ));
        }
        return result;
    }

    public int getProcessedFilesCount(long taskId) {
        ResultSet rs = dbService.getSession().execute(processedFilesCountStatement.bind(taskId));
        Iterator<Row> iterator = rs.iterator();
        if(iterator.hasNext()){
            Row row = iterator.next();
            return row.getInt(CassandraTablesAndColumnsNames.NOTIFICATION_RESOURCE_NUM);
        }else{
            return 0;
        }
    }

    public void removeNotifications(long taskId) {
        dbService.getSession().execute(removeNotificationsByTaskId.bind(taskId));
    }

}
