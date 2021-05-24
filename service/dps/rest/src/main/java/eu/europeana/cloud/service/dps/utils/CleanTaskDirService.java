package eu.europeana.cloud.service.dps.utils;

import eu.europeana.cloud.common.model.dps.TaskInfo;
import eu.europeana.cloud.common.model.dps.TaskState;
import eu.europeana.cloud.service.dps.exceptions.CleanTaskDirException;
import eu.europeana.cloud.service.dps.storm.dao.CassandraTaskInfoDAO;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CleanTaskDirService {
    private static final String SERVICE_CRON_SETUP = "0 0 0/6 * * *";  //daily, every 6 hour

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanTaskDirService.class);
    private static final String TASK_DIR_PREFIX = "task_";
    private static final String TASKDIR_REG_EXP = TASK_DIR_PREFIX + "(-?\\d+)";
    private static final Pattern TASKDIR_PATTERN = Pattern.compile(TASKDIR_REG_EXP);

    @Value("${harvestingTasksDir}")
    private String harvestingTasksDir;

    private CassandraTaskInfoDAO taskInfoDAO;

    private File tasksDir;

    public CleanTaskDirService(CassandraTaskInfoDAO taskInfoDAO) {
        this.taskInfoDAO = taskInfoDAO;
    }

    @PostConstruct
    public void checkHarvestingTasksDir() {
        if (harvestingTasksDir == null)
            throw new NullPointerException("harvestingTasksDir cannot be null");

        tasksDir = new File(harvestingTasksDir);

        if(!tasksDir.exists() || !tasksDir.isDirectory()) {
            LOGGER.error("Given harvesting tasks directory '{}' doesn't exist or it isn't directory", harvestingTasksDir);
        }
    }

    @Scheduled(cron = SERVICE_CRON_SETUP)
    public void serviceTask() {
        File[] dirs = tasksDir.listFiles(file -> {
            Matcher matcher = TASKDIR_PATTERN.matcher(file.getName());
            return file.isDirectory() && matcher.matches();
        });

        if(dirs == null) {
            LOGGER.error("Cannot list subdirectories in: '{}'", tasksDir);
            return;
        }

        for(File dir: dirs) {
            long taskId = getTaskId(dir);

            TaskState taskState = taskInfoDAO.findById(taskId)
                    .map(TaskInfo::getState)
                    .orElse(TaskState.DROPPED);

            if(taskState == TaskState.PROCESSED || taskState == TaskState.DROPPED) {
                try {
                    FileUtils.deleteDirectory(dir);
                }catch(IOException ioe) {
                    LOGGER.error("Cannot delete: '{}' directory", dir.getAbsolutePath());
                }
            }
        }
    }

    public static long getTaskId(File directory) {
        Matcher matcher =  TASKDIR_PATTERN.matcher(directory.getName());

        if(matcher.matches()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch(IllegalStateException | IndexOutOfBoundsException | NumberFormatException e) {
                throw new CleanTaskDirException("Invalid directory name format. It has to match with '" + TASKDIR_REG_EXP + "'", e);
            }
        } else {
            throw new CleanTaskDirException("Invalid directory name format. It has to match with '" + TASKDIR_REG_EXP + "'");
        }
    }

    public static String getDirName(String tasksDirName, long taskId) {
        if (tasksDirName == null)
            throw new NullPointerException("tasksDirName cannot be null");

        if(!tasksDirName.isEmpty() && !tasksDirName.endsWith(File.separator)) {
            tasksDirName += File.separatorChar;
        }

        return tasksDirName + TASK_DIR_PREFIX + taskId;
    }
}
