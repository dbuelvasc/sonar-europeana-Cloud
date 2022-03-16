package migrator;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * @author krystian.
 */
public class Migrator {
    public static final Logger log = LoggerFactory.getLogger(Migrator.class);

    private final static String HOST = "host";
    private final static String PORT = "port";
    private final static String KEY_SPACE = "keySpace";
    private final static String USER = "user";
    private final static String PASSWORD = "password";
    private final static String SERVICE = "service";
    private final static List<String> SERVICES = Arrays.asList("UIS", "MCS", "DPS", "AAS");
    private final static String MIGRATIONS_DIR_PREFIX = "migrations/service/";


    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();

        CliOptions options;
        options = initOptions();

        CommandLine cmd = null;
        try {
            cmd = parser.parse(options.getOptions(), args);
        } catch (ParseException exp) {
            log.error("Reason: " + exp.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("Migrator ", options.getOptions());
            System.exit(1);
        }

        String host = cmd.getOptionValue(HOST);
        String port = cmd.getOptionValue(PORT);
        String keySpace = cmd.getOptionValue(KEY_SPACE);
        String user = cmd.getOptionValue(USER);
        String password = cmd.getOptionValue(PASSWORD);
        String service = cmd.getOptionValue(SERVICE);
        String chosenMigrationDir = "";
        if (SERVICES.contains(service)) {
            chosenMigrationDir = MIGRATIONS_DIR_PREFIX + service.toLowerCase();
        } else {
            log.error("Reason: " + service + " not valid service");
            System.exit(1);
        }

        if (!serviceMatchesKeySpace(service, keySpace)) {
            log.error("Service name: " + service + " does not match keySpace name: " + keySpace);
            System.exit(1);
        }

        MigrationExecutor executor = new MigrationExecutor(keySpace, host, Integer.parseInt(port), user, password, new
                String[]{chosenMigrationDir});
        executor.migrate();
    }

    private static CliOptions initOptions() {
        CliOptions options;
        options = new CliOptions();
        options.addCliSetRequiredOption(HOST, "cassandra host");
        options.addCliSetRequiredOption(PORT, "cassandra port");
        options.addCliSetRequiredOption(KEY_SPACE, "keySpace");
        options.addCliSetRequiredOption(USER, "user");
        options.addCliSetRequiredOption(PASSWORD, "password");
        options.addCliSetRequiredOption(SERVICE, "migrate service eg. (UIS, MCS, DPS, AAS)");
        return options;
    }

    private static boolean serviceMatchesKeySpace(String service, String keySpace) {
        if (keySpace.toLowerCase().contains(service.toLowerCase())) {
            return true;
        } else {
            return false;
        }
    }
}
