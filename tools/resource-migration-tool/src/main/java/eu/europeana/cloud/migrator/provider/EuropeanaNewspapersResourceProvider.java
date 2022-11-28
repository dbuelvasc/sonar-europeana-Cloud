package eu.europeana.cloud.migrator.provider;

import eu.europeana.cloud.common.model.DataProviderProperties;
import eu.europeana.cloud.migrator.ResourceMigrator;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EuropeanaNewspapersResourceProvider
    extends DefaultResourceProvider {

  public static final String IMAGE_DIR = "image";

  private Map<String, String> reversedMapping = new HashMap<String, String>();

  private Map<String, String> duplicateMapping = new HashMap<String, String>();

  private Map<String, Integer> fileCounts = new HashMap<String, Integer>();

  private static final Logger logger = LoggerFactory.getLogger(EuropeanaNewspapersResourceProvider.class);

  public EuropeanaNewspapersResourceProvider(String representationName, String mappingFile, String locations,
      String dataProviderId) throws IOException {
    super(representationName, mappingFile, locations, dataProviderId);
    if (dataProviderId == null) {
      throw new IllegalArgumentException("Data provider identifier must be specified for Europeana Newspapers migration!");
    }
    readMappingFile();
  }

  /**
   * Reads mapping file given while constructing this object. File must be a csv file with ; delimited lists of local identifier
   * and paths to files of the issue. Encoding is UTF-8.
   */
  private void readMappingFile() throws IOException {
    Path mappingPath = null;
    try {
      // try to treat the mapping file as local file
      mappingPath = FileSystems.getDefault().getPath(".", mappingFile);
      if (!mappingPath.toFile().exists()) {
        mappingPath = FileSystems.getDefault().getPath(mappingFile);
      }
    } catch (InvalidPathException e) {
      // in case path cannot be created try to treat the mapping file as absolute path
      mappingPath = FileSystems.getDefault().getPath(mappingFile);
      logger.info("Invalid Path exception. Mapping file {} as absolute path: {}",
          mappingFile,
          mappingPath);
    }
    if (mappingPath == null || !mappingPath.toFile().exists()) {
      throw new IOException("Mapping file cannot be found: " + mappingFile);
    }

    String localId;
    String path;
    List<String> paths = new ArrayList<String>();
    BufferedReader reader = null;

    try {
      reader = Files.newBufferedReader(mappingPath, StandardCharsets.UTF_8);
      for (; ; ) {
        String line = reader.readLine();
        if (line == null) {
          break;
        }
        StringTokenizer tokenizer = new StringTokenizer(line, ";");
        // first token is local identifier
        if (tokenizer.hasMoreTokens()) {
          localId = tokenizer.nextToken().trim();
        } else {
          localId = null;
        }
        if (localId == null) {
          logger.warn("Local identifier is null ({}). Skipping line.", localId);
          continue;
        }

        boolean duplicate = false;

        paths.clear();
        int count = 0;

        while (tokenizer.hasMoreTokens()) {
          path = tokenizer.nextToken().trim();
          // when path is empty do not add to map
          if (path.isEmpty()) {
            continue;
          }
          if (reversedMapping.get(path) != null && !duplicate) {
            logger.warn("File {} already has a local id = {}}. New local id = {}",
                path,
                reversedMapping.get(path),
                localId);
            duplicate = true;
            for (String s : paths) {
              reversedMapping.remove(s);
              duplicateMapping.put(s, localId.intern());
            }
          }
          if (duplicate)
          // add reversed mapping to duplicates map
          {
            duplicateMapping.put(path, localId.intern());
          } else {
            // add reversed mapping
            reversedMapping.put(path.intern(), localId.intern());
            paths.add(path.intern());
          }
          count++;
        }
        fileCounts.put(localId.intern(), Integer.valueOf(count));
      }
    } finally {
      if (reader != null) {
        reader.close();
      }
    }
  }

  @Override
  public String getResourceProviderId(String path) {
    if (!path.contains(IMAGE_DIR)) {
      logger.error("No image directory found in resource path.");
      return null;
    }

    int pos = path.indexOf(IMAGE_DIR);
    String rest = path.substring(pos + IMAGE_DIR.length());
    if (rest.startsWith(ResourceMigrator.LINUX_SEPARATOR) || rest.startsWith(ResourceMigrator.WINDOWS_SEPARATOR)) {
      rest = rest.substring(1);
    }
    pos = rest.indexOf(ResourceMigrator.LINUX_SEPARATOR);
    if (pos == -1) {
      pos = rest.indexOf(ResourceMigrator.WINDOWS_SEPARATOR);
    }

    return rest.substring(0, pos > -1 ? pos : rest.length());
  }

  @Override
  public String getDataProviderId(String path) {
    // path is not used to determine data provider, always use the configured data provider
    return dataProviderId;
  }

  @Override
  public DataProviderProperties getDataProviderProperties(String path) {
    String id = getDataProviderId(path);
    if (id == null) {
      // something is wrong, data provider not specified, this should never happen
      throw new IllegalArgumentException("Data provider identifier must be specified for Europeana Newspapers migration!");
    }

    File f = new File(path);
    // when file is id.properties return properties from file
    if (f.exists() && f.isFile() && f.getName().equals(id + PROPERTIES_EXTENSION)) {
      return getDataProviderPropertiesFromFile(f);
    }

    // when file is directory try to search for file id.properties inside
    if (f.isDirectory()) {
      File dpFile = new File(f, id + PROPERTIES_EXTENSION);
      if (dpFile.exists()) {
        return getDataProviderPropertiesFromFile(dpFile);
      }
    }

    return getDefaultDataProviderProperties();
  }

  @Override
  public String getLocalIdentifier(String location, String path, boolean duplicate) {
    // first get the local path within location
    String localPath = getLocalPath(location, path);
    // we have to find the identifier in the mapping file
    String localId = duplicate ? duplicateMapping.get(localPath) : reversedMapping.get(localPath);
    // when searching in normal mapping and id is not found display a warning
    if (localId == null && !duplicate) {
      logger.warn("Local identifier for file {} was not found in the mapping file!", localPath);
    }
    return localId;
  }

  private String getLocalPath(String location, String path) {
    int i = path.indexOf(location);
    if (i == -1) {
      return path;
    }
    return path.substring(i + location.length() + 1);
  }

  @Override
  public int getFileCount(String localId) {
    Integer count = fileCounts.get(localId);
    if (count == null) {
      return -1;
    }
    return count.intValue();
  }

  @Override
  public List<FilePaths> split(List<FilePaths> paths) {
    List<FilePaths> result = new ArrayList<FilePaths>();
    for (FilePaths fp : paths) {
      result.addAll(split(fp, true));
    }
    return result;
  }

  private List<FilePaths> split(FilePaths fp, boolean year) {
    // split will be done for every newspaper title which is the directory just inside the provider directory
    List<FilePaths> result = new ArrayList<FilePaths>();
    Map<String, List<String>> titlePaths = new HashMap<String, List<String>>();

    BufferedReader pathsReader = fp.getPathsReader();

    try {
      for (; ; ) {
        String path = pathsReader.readLine();
        if (path == null) {
          break;
        }

        int i = path.indexOf(fp.getLocation());
        i = path.indexOf(fp.getDataProvider(), i == -1 ? 0 : i + fp.getLocation().length() + 1);
        if (i == -1) {
          // no data provider name in path, strange so return the FilePaths object unchanged regardless the other paths could contain provider name
          result.add(fp);
          return result;
        }
        String title = path.substring(i + fp.getDataProvider().length() + 1);
        i = title.indexOf(ResourceMigrator.LINUX_SEPARATOR);
        if (i == -1) {
          // no directory found in path, strange so return the FilePaths object unchanged regardless the other paths
          result.add(fp);
          return result;
        }
        if (year) {
          // add year to title, for every year of a title there will be a separate thread
          // find next separator
          int j = title.indexOf(ResourceMigrator.LINUX_SEPARATOR, i + 1);
          String yearStr = title.substring(i + 1, j);
          if (yearStr.length() < 4) {
            i = title.indexOf(ResourceMigrator.LINUX_SEPARATOR, j + 1);
          } else {
            i = j;
          }
        }
        title = title.substring(0, i);
        if (titlePaths.get(title) == null) {
          titlePaths.put(title, new ArrayList<String>());
        }
        titlePaths.get(title).add(path);
      }
    } catch (IOException e) {
      logger.error("Cannot read paths file for location {} and provider {}",
          fp.getLocation(),
          fp.getDataProvider()
      );
    } finally {
      if (pathsReader != null) {
        try {
          pathsReader.close();
        } catch (IOException e) {
          logger.error(e.getMessage(), e);
        }
      }
    }

    if (titlePaths.size() == 1) {
      // all paths belong to the same title so no need to create a new FilePaths object as it would be the same as the input one
      result.add(fp);
    } else {
      // now create FilePaths object for every newspapers title
      for (Map.Entry<String, List<String>> entry : titlePaths.entrySet()) {
        FilePaths filePaths = new FilePaths(fp.getLocation(), fp.getDataProvider());
        filePaths.setIdentifier(entry.getKey().replace(ResourceMigrator.LINUX_SEPARATOR, "_"));
        filePaths.useFile(filePaths.getIdentifier());
        filePaths.addPaths(entry.getValue());
        result.add(filePaths);
      }
    }
    return result;
  }

  @Override
  public boolean usePathsFile() {
    return true;
  }

  @Override
  public Map<String, String> getReversedMapping() {
    return reversedMapping;
  }
}
