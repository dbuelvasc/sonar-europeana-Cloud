package eu.europeana.cloud.service.dps.examples;

import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import eu.europeana.cloud.cassandra.CassandraConnectionProviderSingleton;
import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.InputDataType;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.examples.toplologies.constants.TopologyConstants;
import eu.europeana.cloud.service.dps.storm.StormTaskTuple;
import eu.europeana.cloud.service.dps.storm.StormTupleKeys;
import eu.europeana.cloud.service.dps.storm.utils.TaskStatusUpdater;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Always uses the same {@link DpsTask} (instead of consuming tasks from a Kafka Topic)
 * <p>
 * Useful for testing without having Kafka deployed.
 *
 * @author manos
 */
public class StaticDpsTaskSpout extends BaseRichSpout {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticDpsTaskSpout.class);

    private SpoutOutputCollector collector;

    /**
     * The task to be consumed
     */
    private DpsTask task;

    public StaticDpsTaskSpout(DpsTask task, String topologyName) {
        this.task = task;
        initTaskInfo(topologyName);
    }

    private void initTaskInfo(String topologyName) {
        CassandraConnectionProvider cassandraConnectionProvider = CassandraConnectionProviderSingleton.getCassandraConnectionProvider(TopologyConstants.CASSANDRA_HOSTS, Integer.parseInt(TopologyConstants.CASSANDRA_PORT), TopologyConstants.CASSANDRA_KEYSPACE_NAME, TopologyConstants.CASSANDRA_USERNAME, TopologyConstants.CASSANDRA_SECRET_TOKEN);
        TaskStatusUpdater taskInfoDAO = TaskStatusUpdater.getInstance(cassandraConnectionProvider);
        taskInfoDAO.insert(task.getTaskId(), topologyName, 0, String.valueOf(TaskState.CURRENTLY_PROCESSING), "", new Date(), null);
    }

    @Override
    public void open(Map conf, TopologyContext context,
                     SpoutOutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void nextTuple() {

        try {
            Map<String, String> taskParameters = task.getParameters();
            LOGGER.info("taskParameters size= {} ", taskParameters.size());

            List<String> sources = task.getDataEntry(InputDataType.REPOSITORY_URLS);
            if (sources == null) {
                sources = task.getDataEntry(InputDataType.DATASET_URLS);
            }
            LOGGER.info("Sources size {}", sources.size());

            String dataEntry = convertListToString(sources);
            taskParameters.put(PluginParameterKeys.DPS_TASK_INPUT_DATA, dataEntry);

            for (String sourceURL : sources) {
                LOGGER.info("emitting... {}", sourceURL);
                collector.emit(new StormTaskTuple(task.getTaskId(), task.getTaskName(), sourceURL, null, taskParameters, task.getOutputRevision(), task.getHarvestingDetails()).toStormTuple());
            }

            Utils.sleep(6000000);

        } catch (Exception e) {
            LOGGER.error("StaticDpsTaskSpout error: {}", e.getMessage());
        }
    }

    @Override
    public void ack(Object id) {
    }

    @Override
    public void fail(Object id) {
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(
                StormTupleKeys.TASK_ID_TUPLE_KEY,
                StormTupleKeys.TASK_NAME_TUPLE_KEY,
                StormTupleKeys.INPUT_FILES_TUPLE_KEY,
                StormTupleKeys.FILE_CONTENT_TUPLE_KEY,
                StormTupleKeys.PARAMETERS_TUPLE_KEY,
                StormTupleKeys.REVISIONS,
                StormTupleKeys.SOURCE_TO_HARVEST));
    }

    private String convertListToString(List<String> list) {
        String listString = list.toString();
        return listString.substring(1, listString.length() - 1);

    }
}