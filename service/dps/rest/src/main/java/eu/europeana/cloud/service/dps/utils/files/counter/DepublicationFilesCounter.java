package eu.europeana.cloud.service.dps.utils.files.counter;

import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.depublish.DatasetDepublisher;
import eu.europeana.cloud.service.dps.exceptions.TaskSubmissionException;
import eu.europeana.cloud.service.dps.storm.utils.SubmitTaskParameters;
import eu.europeana.indexing.exception.IndexingException;

import java.io.IOException;
import java.net.URISyntaxException;

public class DepublicationFilesCounter extends FilesCounter {

  private final DatasetDepublisher depublisher;

  public DepublicationFilesCounter(DatasetDepublisher depublisher) {
    this.depublisher = depublisher;
  }

  @Override
  public int getFilesCount(DpsTask task) throws TaskSubmissionException {
    if (task.getParameter(PluginParameterKeys.RECORD_IDS_TO_DEPUBLISH) != null) {
      return calculateRecordsNumber(task);
    }
    if (task.getParameter(PluginParameterKeys.METIS_DATASET_ID) != null) {
      return calculateDatasetSize(task);
    }
    throw new TaskSubmissionException("Can't evaluate task expected size! Needed parameters not found in the task");

  }

  private int calculateDatasetSize(DpsTask task) throws TaskSubmissionException {
    try {
      long expectedSize = depublisher.getRecordsCount(SubmitTaskParameters.builder().task(task).build());

      if (expectedSize > Integer.MAX_VALUE) {
        throw new TaskSubmissionException(
            "There are " + expectedSize + " records in set. It exceeds Integer size and is not supported.");
      }
      if (expectedSize <= 0) {
        throw new TaskSubmissionException("Not found any publicised records of dataset for task " + task.getTaskId());
      }
      return (int) expectedSize;
    } catch (IndexingException e) {
      throw new TaskSubmissionException("Can't evaluate task expected size!", e);
    }
  }

  private int calculateRecordsNumber(DpsTask task) {
    return task.getParameter(PluginParameterKeys.RECORD_IDS_TO_DEPUBLISH).split(",").length;
  }
}
