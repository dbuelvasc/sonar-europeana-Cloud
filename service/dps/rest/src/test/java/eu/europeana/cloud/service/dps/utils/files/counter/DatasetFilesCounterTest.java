package eu.europeana.cloud.service.dps.utils.files.counter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.driver.core.exceptions.NoHostAvailableException;
import eu.europeana.cloud.common.model.dps.TaskInfo;
import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.cloud.service.dps.DpsTask;
import eu.europeana.cloud.service.dps.PluginParameterKeys;
import eu.europeana.cloud.service.dps.exceptions.TaskSubmissionException;
import eu.europeana.cloud.service.dps.storm.dao.CassandraTaskInfoDAO;
import java.util.Date;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class DatasetFilesCounterTest {

  private DatasetFilesCounter datasetFilesCounter;
  private CassandraTaskInfoDAO taskInfoDAO;
  private final static long TASK_ID = 1234;
  private final static int EXPECTED_SIZE = 100;
  private final static int DEFAULT_FILES_COUNT = -1;
  private final static String TOPOLOGY_NAME = "TOPOLOGY_NAME";
  private DpsTask dpsTask;


  @Before
  public void init() {
    taskInfoDAO = mock(CassandraTaskInfoDAO.class);
    datasetFilesCounter = new DatasetFilesCounter(taskInfoDAO);
    dpsTask = new DpsTask();
  }


  @Test
  public void shouldReturnProcessedFiles() throws Exception {
    TaskInfo taskInfo = TaskInfo.builder()
                                .id(TASK_ID)
                                .topologyName(TOPOLOGY_NAME)
                                .state(TaskState.PROCESSED)
                                .stateDescription("")
                                .expectedRecordsNumber(EXPECTED_SIZE)
                                .processedRecordsCount(EXPECTED_SIZE)
                                .processedErrorsCount(0)
                                .sentTimestamp(new Date())
                                .startTimestamp(new Date())
                                .finishTimestamp(new Date())
                                .build();
    when(taskInfoDAO.findById(TASK_ID)).thenReturn(Optional.of(taskInfo));
    dpsTask.addParameter(PluginParameterKeys.PREVIOUS_TASK_ID, String.valueOf(TASK_ID));
    int expectedFilesCount = datasetFilesCounter.getFilesCount(dpsTask);
    assertEquals(EXPECTED_SIZE, expectedFilesCount);
  }

  @Test
  public void shouldReturnProperNumberOfRecordsForNextTopology() throws Exception {
    TaskInfo taskInfo = TaskInfo.builder()
                                .id(TASK_ID)
                                .topologyName(TOPOLOGY_NAME)
                                .state(TaskState.PROCESSED)
                                .stateDescription("")
                                .expectedRecordsNumber(EXPECTED_SIZE)
                                .processedRecordsCount(EXPECTED_SIZE)
                                .deletedRecordsCount(10)
                                .postProcessedRecordsCount(10)
                                .processedErrorsCount(0)
                                .sentTimestamp(new Date())
                                .startTimestamp(new Date())
                                .finishTimestamp(new Date())
                                .build();
    when(taskInfoDAO.findById(TASK_ID)).thenReturn(Optional.of(taskInfo));
    dpsTask.addParameter(PluginParameterKeys.PREVIOUS_TASK_ID, String.valueOf(TASK_ID));
    int expectedFilesCount = datasetFilesCounter.getFilesCount(dpsTask);
    assertEquals(EXPECTED_SIZE + 10 + 10, expectedFilesCount);
  }

  @Test
  public void shouldReturnedDefaultFilesCountWhenNoPreviousTaskIdIsProvided() throws Exception {
    int expectedFilesCount = datasetFilesCounter.getFilesCount(dpsTask);
    assertEquals(DEFAULT_FILES_COUNT, expectedFilesCount);
  }

  @Test
  public void shouldReturnedDefaultFilesCountWhenPreviousTaskIdIsNotLongValue() throws Exception {
    int expectedFilesCount = datasetFilesCounter.getFilesCount(dpsTask);
    dpsTask.addParameter(PluginParameterKeys.PREVIOUS_TASK_ID, "Not long value");
    assertEquals(DEFAULT_FILES_COUNT, expectedFilesCount);
  }

  @Test
  public void shouldReturnedDefaultFilesCountWhenPreviousTaskIdDoesNotExist() throws Exception {
    dpsTask.addParameter(PluginParameterKeys.PREVIOUS_TASK_ID, String.valueOf(TASK_ID));
    doReturn(Optional.empty()).when(taskInfoDAO).findById(TASK_ID);
    int expectedFilesCount = datasetFilesCounter.getFilesCount(dpsTask);
    assertEquals(DEFAULT_FILES_COUNT, expectedFilesCount);
  }

  @Test(expected = TaskSubmissionException.class)
  public void shouldThrowExceptionWhenQueryingDatabaseUsingPreviousTaskIdThrowAnExceptionOtherThanTaskInfoDoesNotExistException()
      throws Exception {
    dpsTask.addParameter(PluginParameterKeys.PREVIOUS_TASK_ID, String.valueOf(TASK_ID));
    doThrow(NoHostAvailableException.class).when(taskInfoDAO).findById(TASK_ID);
    datasetFilesCounter.getFilesCount(dpsTask);
  }


}