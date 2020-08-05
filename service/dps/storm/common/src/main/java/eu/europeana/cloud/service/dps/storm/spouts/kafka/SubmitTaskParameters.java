package eu.europeana.cloud.service.dps.storm.spouts.kafka;

import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Set of parameters to submit task to
 */
@Builder
@Getter
@Setter
@ToString
public class SubmitTaskParameters {

    //This incomplete builder implementation is needed to valid field initialization.
    //In absence of this, new class created by Lombok autogenerated builder would have uninitialized fields.
    //Rest functionality of builder is generated automatillay by Lombok, acording to @Builder annotation.
    public static class SubmitTaskParametersBuilder {
        private int expectedSize = 0;
        private AtomicInteger sentRecordsCounter = new AtomicInteger();
    }

    private Date sentTime;

    private int expectedSize;

    private TaskState status;

    private String info;

    /**
     * Submitting task
     */
    private DpsTask task;

    /**
     * Name of processing topology
     */
    private String topologyName;

    /**
     * Name of processing topic selected for task
     */
    private String topicName;

    /**
     * Flag if task is subimtted <code>false<code/> or restarted <code>true<code/>
     */
    private final boolean restart;

    private String taskJSON;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private AtomicInteger sentRecordsCounter;

    public int incrementAndGetSentRecordCounter() {
        return sentRecordsCounter.incrementAndGet();
    }

    public String getTaskParameter(String parameterKey){
        return task.getParameter(parameterKey);
    }

    public boolean getUseAlternativeEnvironment() {
        return Boolean.parseBoolean(task.getParameter(PluginParameterKeys.METIS_USE_ALT_INDEXING_ENV));
    }
}