package eu.europeana.cloud.migrator;

import eu.europeana.cloud.client.uis.rest.CloudException;
import eu.europeana.cloud.client.uis.rest.UISClient;
import eu.europeana.cloud.common.model.CloudId;
import eu.europeana.cloud.common.model.DataProviderProperties;
import eu.europeana.cloud.common.model.Representation;
import eu.europeana.cloud.mcs.driver.FileServiceClient;
import eu.europeana.cloud.mcs.driver.RecordServiceClient;
import eu.europeana.cloud.migrator.processing.FileProcessor;
import eu.europeana.cloud.migrator.processing.FileProcessorFactory;
import eu.europeana.cloud.migrator.provider.Cleaner;
import eu.europeana.cloud.migrator.provider.DefaultResourceProvider;
import eu.europeana.cloud.migrator.provider.FilePaths;
import eu.europeana.cloud.migrator.provider.ResourceProvider;
import eu.europeana.cloud.service.mcs.exception.MCSException;
import eu.europeana.cloud.service.mcs.exception.ProviderNotExistsException;
import eu.europeana.cloud.service.mcs.exception.RecordNotExistsException;
import eu.europeana.cloud.service.mcs.exception.RepresentationNotExistsException;
import eu.europeana.cloud.service.uis.exception.ProviderAlreadyExistsException;
import eu.europeana.cloud.service.uis.exception.RecordDoesNotExistException;
import eu.europeana.cloud.service.uis.exception.RecordExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

public class ResourceMigrator {

    public static final String LINUX_SEPARATOR = "/";

    public static final String WINDOWS_SEPARATOR = "\\";

    public static final String TEXT_EXTENSION = ".txt";

    private static final String FILES_PART = "files";

    private static final Logger logger = LoggerFactory.getLogger(ResourceMigrator.class);

    private static final Map<String, String> mimeTypes = new HashMap<>();

    static {
        mimeTypes.put("jp2", "image/jp2");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("tif", "image/tiff");
        mimeTypes.put("tiff", "image/tiff");
        mimeTypes.put("xml", "text/xml");
    }

    // default number of retries
    private static final int DEFAULT_RETRIES = 10;

    // Default threads pool size
    private static final byte DEFAULT_POOL_SIZE = 10;

    // Pool of threads used to migrate files
    private static final ExecutorService threadPool = Executors
            .newFixedThreadPool(DEFAULT_POOL_SIZE);

    // Default threads pool size
    private static final byte DEFAULT_PROVIDER_POOL_SIZE = 50;

    /**
     * MCS java client
     */
    @Autowired
    private RecordServiceClient mcs;

    /**
     * UIS java client
     */
    @Autowired
    private UISClient uis;

    /**
     * MCS files java client
     */
    @Autowired
    private FileServiceClient fsc;

    /**
     * Resource provider, specific implementation will be used in runtime, eg. FoodAndDrinkResourceProvider
     */
    private ResourceProvider resourceProvider;


    /**
     * FileProcessor is an interface that supplies functions for some processing on the original files before they are uploaded to ECloud.
     */
    private FileProcessor fileProcessor;

    /**
     * Key is directory name, value is identifier that should be used in ECloud
     */
    protected Map<String, String> dataProvidersMapping;

    protected int threadsCount = DEFAULT_PROVIDER_POOL_SIZE;

    public ResourceMigrator(ResourceProvider resProvider, String dataProvidersMappingFile, String threadsCount, FileProcessorFactory fileProcessorFactory) throws IOException {
        this.resourceProvider = resProvider;
        this.dataProvidersMapping = readDataProvidersMapping(dataProvidersMappingFile);
        if (threadsCount != null && !threadsCount.isEmpty()) {
            try {
                this.threadsCount = Integer.parseInt(threadsCount);
                if (this.threadsCount < 0)
                    this.threadsCount = DEFAULT_PROVIDER_POOL_SIZE;
            } catch (NumberFormatException e) {
                // leave the default value
            }
        }
        this.fileProcessor = fileProcessorFactory.create();
    }

    private Map<String, String> readDataProvidersMapping(String dataProvidersMappingFile) throws IOException {
        Map<String, String> mapping = new HashMap<>();
        // data providers mapping is optional so return empty map when not provided
        if (dataProvidersMappingFile == null || dataProvidersMappingFile.isEmpty())
            return mapping;

        Path mappingPath = null;
        try {
            // try to treat the mapping file as local file
            mappingPath = FileSystems.getDefault().getPath(".", dataProvidersMappingFile);
            if (!mappingPath.toFile().exists())
                mappingPath = FileSystems.getDefault().getPath(dataProvidersMappingFile);
        } catch (InvalidPathException e) {
            // in case path cannot be created try to treat the mapping file as absolute path
            mappingPath = FileSystems.getDefault().getPath(dataProvidersMappingFile);
        }
        if (mappingPath == null || !mappingPath.toFile().exists())
            throw new IOException("Mapping file cannot be found: " + dataProvidersMappingFile);

        String directory;
        String identifier;

        for (String line : Files.readAllLines(mappingPath, StandardCharsets.UTF_8)) {
            if (line == null)
                break;
            StringTokenizer tokenizer = new StringTokenizer(line, ";");
            // first token is directory name
            if (tokenizer.hasMoreTokens())
                directory = tokenizer.nextToken().trim();
            else
                directory = null;
            if (directory == null || directory.isEmpty()) {
                logger.warn("Directory name for data provider is missing ({}). Skipping line.", directory);
                continue;
            }

            if (tokenizer.hasMoreTokens())
                identifier = tokenizer.nextToken().trim();
            else
                identifier = null;

            // when identifier is null or empty do not add to map
            if (identifier == null || identifier.isEmpty())
                continue;
            mapping.put(directory, identifier);
        }

        return mapping;
    }

    public void verifyLocalIds() {
        Set<String> uniqueIds = new HashSet<>();
        uniqueIds.addAll(resourceProvider.getReversedMapping().values());

        // do nothing when local identifiers could not be retrieved
        if (uniqueIds.size() == 0)
            return;

        ExecutorService threadLocalIdPool = Executors
                .newFixedThreadPool(threadsCount);
        List<Future<LocalIdVerificationResult>> results = null;

        int parts = threadsCount;

        int idsPerThread = uniqueIds.size() / threadsCount;
        if (idsPerThread == 0) {
            parts = 1;
            idsPerThread = uniqueIds.size();
        } else {
            if (uniqueIds.size() % threadsCount > 0)
                idsPerThread++;
        }

        List<Callable<LocalIdVerificationResult>> tasks = new ArrayList<>(parts);
        List<String> localIds = new ArrayList<>(uniqueIds);
        // create task for each resource provider
        for (int i = 0; i < parts; i++) {
            int from = i * idsPerThread;
            int to = (i + 1) * idsPerThread > localIds.size() ? localIds.size() : (i + 1) * idsPerThread;
            String id = String.valueOf(from + "-" + to);
            logger.info("Starting local identifier verification task thread for part {}...", id);
            tasks.add(new LocalIdVerifier(localIds.subList(from, to), id));
        }

        if (tasks.isEmpty())
            return;

        try {
            // invoke a separate thread for each provider
            results = threadLocalIdPool.invokeAll(tasks);

            LocalIdVerificationResult localIdResult;
            for (Future<LocalIdVerificationResult> result : results) {
                localIdResult = result.get();
                logger.info("Verification of local identifier part {} performed successfully. Verification time: {} sec. Number of not migrated identifiers: {}",
                        localIdResult.getIdentifier(),
                        localIdResult.getTime(),
                        localIdResult.getNotMigratedCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Verification processed interrupted.", e);
        } catch (ExecutionException e) {
            logger.error("Problem with verification task thread execution.", e);
        }
    }

    private String getPathFromMapping(Map<String, String> localIds, String localId) {
        if (localIds == null || localIds.size() == 0)
            return null;

        String result = null;

        for (Map.Entry<String, String> entry : localIds.entrySet()) {
            if (entry.getValue().equals(localId)) {
                int pos = entry.getKey().lastIndexOf(LINUX_SEPARATOR);
                result = entry.getKey().substring(0, pos != -1 ? pos : entry.getKey().length());
                break;
            }
        }
        return result;
    }

    public boolean grant(boolean simulate) {
        Set<String> uniqueIds = new HashSet<>();
        uniqueIds.addAll(resourceProvider.getReversedMapping().values());

        // do nothing when local identifiers could not be retrieved
        if (uniqueIds.isEmpty())
            return false;

        ExecutorService threadLocalIdPool = Executors
                .newFixedThreadPool(threadsCount);
        List<Future<PublicAccessGranterResult>> results = null;
        List<Callable<PublicAccessGranterResult>> tasks = new ArrayList<>();

        int parts = threadsCount;

        int idsPerThread = uniqueIds.size() / threadsCount;
        if (idsPerThread == 0) {
            parts = 1;
            idsPerThread = uniqueIds.size();
        } else {
            if (uniqueIds.size() % threadsCount > 0)
                idsPerThread++;
        }

        List<String> localIds = new ArrayList<>(uniqueIds);
        // create task for each resource provider
        for (int i = 0; i < parts; i++) {
            int from = i * idsPerThread;
            if (from >= localIds.size()) {
                break;
            }
            int to = (i + 1) * idsPerThread > localIds.size() ? localIds.size() : (i + 1) * idsPerThread;
            String id = String.valueOf(from + "-" + to);
            logger.info("Starting public access granter task thread for part {}...", id);
            tasks.add(new PublicAccessGranter(localIds.subList(from, to), id, simulate));
        }

        if (tasks.size() == 0)
            return false;

        try {
            // invoke a separate thread for each provider
            results = threadLocalIdPool.invokeAll(tasks);

            PublicAccessGranterResult granterResult;
            for (Future<PublicAccessGranterResult> result : results) {
                granterResult = result.get();
                logger.info("Public access granter part {} performed successfully. Granting time: {} sec. Number of not granted identifiers: {}",
                        granterResult.getIdentifier(), granterResult.getTime(), granterResult.getNotGrantedCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Granting processed interrupted.", e);
        } catch (ExecutionException e) {
            logger.error("Problem with granting task thread execution.", e);
        }
        return true;
    }

    public boolean migrate(boolean clean, boolean simulate) {
        boolean success = true;

        long start = System.currentTimeMillis();

        // key is provider id, value is a list of files to add
        Map<String, List<FilePaths>> paths = resourceProvider.scan();

        logger.info("Scanning resource provider locations finished in {} sec.", ((float) (System.currentTimeMillis() - start) / (float) 1000));

        // when simulate is true just a simulation is performed - it scans the locations and stores summary to a file
        if (!clean && simulate) {
            summarize(paths);
            return true;
        }

        List<Future<MigrationResult>> results = null;
        List<Callable<MigrationResult>> tasks = new ArrayList<>();

        // create task for each resource provider
        Iterator<Entry<String, List<FilePaths>>> iterator = paths.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, List<FilePaths>> entry = iterator.next();

            if (clean) {
                logger.info("Cleaning {}", entry.getKey());
                clean(entry.getKey());
            }
            if (!simulate) {
                logger.info("Starting task thread for provider {}...", entry.getKey());
                tasks.add(new ProviderMigrator(entry.getKey(), entry.getValue(), null));
            }
        }

        // when simulation mode is on no tasks to invoke should be created, return immediately
        if (tasks.isEmpty())
            return success;

        try {
            // invoke a separate thread for each provider
            results = threadPool.invokeAll(tasks);

            MigrationResult providerResult;
            for (Future<MigrationResult> result : results) {
                providerResult = result.get();
                logger.info("Migration of provider {} performed. Was successful: {}. Migration time: {} sec.",
                        providerResult.getProviderId(),
                        providerResult.isSuccessful(),
                        providerResult.getTime());
                success &= providerResult.isSuccessful();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Migration processed interrupted.", e);
        } catch (ExecutionException e) {
            logger.error("Problem with migration task thread execution.", e);
        }

        return success;
    }

    private void summarize(Map<String, List<FilePaths>> paths) {
        String summaryFile = "simulation" + System.currentTimeMillis() + ResourceMigrator.TEXT_EXTENSION;
        try {
            // create a file called simulation{timestamp}.txt

            Path dest = FileSystems.getDefault().getPath(".", summaryFile);
            String msg = "Found " + paths.size() + " data providers.\n";
            Files.write(dest, msg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            for (Map.Entry<String, List<FilePaths>> entry : paths.entrySet()) {
                msg = "\nData provider " + entry.getKey();
                if (dataProvidersMapping.get(entry.getKey()) != null)
                    msg += " (mapped: " + dataProvidersMapping.get(entry.getKey()) + ")";
                msg += ": " + entry.getValue().size() + " locations\n";
                Files.write(dest, msg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                for (FilePaths fp : entry.getValue()) {
                    BufferedReader reader = fp.getPathsReader();
                    try {
                        msg = "\nLocation: " + fp.getLocation() + " - " + fp.size() + " paths\n\n";
                        Files.write(dest, msg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                        int counter = 1;
                        if (reader != null) {
                            for (; ; ) {
                                String s = reader.readLine();
                                if (s == null)
                                    break;
                                Files.write(dest, String.valueOf(counter++ + ". " + s + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                            }
                        } else {
                            for (String s : fp.getFullPaths()) {
                                Files.write(dest, String.valueOf(counter++ + ". " + s + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                            }
                        }
                    } finally {
                        if (reader != null)
                            reader.close();
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Summary file " + summaryFile + " could not be saved.", e);
        }
    }

    /**
     * Create representation for the specified provider and cloud identifier. Representation name is supplied by the resource provider.
     * If there is already a representation and it is persistent then we cannot add anything to this record. If there are non persistent
     * representations they will be removed. Please, be careful when calling this method.
     *
     * @param providerId data provider identifier
     * @param cloudId    cloud identifier
     * @return URI of the created representation and version
     */
    private URI createRepresentationName(String providerId, String cloudId) {
        int retries = DEFAULT_RETRIES;
        // get the mapped identifier if any
        String dataProviderId = getProviderId(providerId);
        while (retries-- > 0) {
            try {
                // get all representations
                List<Representation> representations;

                try {
                    representations = mcs.getRepresentations(cloudId, resourceProvider.getRepresentationName());
                } catch (RepresentationNotExistsException e) {
                    // when there are no representations add a new one
                    return mcs.createRepresentation(cloudId, resourceProvider.getRepresentationName(), dataProviderId);
                }

                // when there are some old representations it means that somebody had to add them, delete non persistent ones
                for (Representation representation : representations) {
                    if (representation.isPersistent())
                        return null;
                    else
                        mcs.deleteRepresentation(cloudId, resourceProvider.getRepresentationName(), representation.getVersion());
                }
                // when there are no representations add a new one
                return mcs.createRepresentation(cloudId, resourceProvider.getRepresentationName(), dataProviderId);
            } catch (ProcessingException e) {
                logger.warn("Error processing HTTP request while creating representation for record {}, provider {}. Retries left: {}",
                        cloudId,
                        dataProviderId,
                        retries,
                        e);
            } catch (ProviderNotExistsException e) {
                logger.error("Provider {} does not exist!", dataProviderId);
                break;
            } catch (RecordNotExistsException e) {
                logger.error("Record {} does not exist!", cloudId);
                break;
            } catch (MCSException e) {
                logger.error("Problem with creating representation name!");
            } catch (Exception e) {
                logger.error("Exception when creating representation occured.", e);
            }
        }
        logger.warn("All attempts to create representation failed. ProviderId: {} CloudId: {} Representation: {}",
                providerId,
                cloudId,
                resourceProvider.getRepresentationName());
        return null;
    }

    /**
     * Create new record for specified provider and local identifier. If record already exists return its identifier.
     *
     * @param providerId data provider identifier
     * @param localId    local identifier of the record
     * @return newly created cloud identifier or existing cloud identifier
     */
    private String createRecord(String providerId, String localId) {
        int retries = DEFAULT_RETRIES;
        // get mapped data provider identifier if any
        String dataProviderId = getProviderId(providerId);
        while (retries-- > 0) {
            try {
                CloudId cloudId = uis.createCloudId(dataProviderId, localId);
                if (cloudId != null)
                    return cloudId.getId();
            } catch (ProcessingException e) {
                logger.warn("Error processing HTTP request while creating record for provider {} and local id {}. Retries left: {}",
                        dataProviderId,
                        localId,
                        retries,
                        e);
            } catch (CloudException e) {
                if (e.getCause() instanceof RecordExistsException) {
                    try {
                        logger.info("Record Exists {}", localId);
                        return uis.getCloudId(dataProviderId, localId).getId();
                    } catch (ProcessingException e1) {
                        logger.warn("Error processing HTTP request while creating record for provider {}. Retries left: {}",
                                dataProviderId,
                                retries,
                                e);
                    } catch (CloudException e1) {
                        logger.warn("Record for provider {} with local id {} could not be created",
                                dataProviderId,
                                localId,
                                e1);
                        break;
                    } catch (Exception e1) {
                        logger.info("Provider: {} Local: {}",
                                providerId,
                                localId,
                                e);
                    }
                } else {
                    logger.warn("Record for provider {} with local id {} could not be created",
                            dataProviderId,
                            localId,
                            e);
                    break;
                }
            } catch (Exception e) {
                logger.error("Exception when creating record occured.", e);
            }
        }
        logger.warn("All attempts to create record failed. ProviderId: {} LocalId: {}",
                providerId,
                localId);
        return null;
    }


    /**
     * Create new record for specified provider and local identifier. If record already exists return its identifier.
     *
     * @param providerId data provider identifier
     * @param localId    local identifier of the record
     * @return newly created cloud identifier or existing cloud identifier
     */
    private String getRecord(String providerId, String localId) {
        int retries = DEFAULT_RETRIES;
        // get mapped data provider identifier if any
        String dataProviderId = getProviderId(providerId);
        while (retries-- > 0) {
            try {
                CloudId cloudId = uis.getCloudId(dataProviderId, localId);
                if (cloudId != null)
                    return cloudId.getId();
            } catch (ProcessingException e) {
                logger.warn("Error processing HTTP request while getting record for provider {} and local id {}. Retries left: {}",
                        dataProviderId,
                        localId,
                        retries,
                        e);
            } catch (CloudException e) {
                if (e.getCause() instanceof RecordDoesNotExistException) {
                    return null;
                }
            } catch (Exception e) {
                logger.error("Exception when getting record occured.", e);
            }
        }
        logger.warn("All attempts to get record failed. ProviderId: {} LocalId: {}",
                providerId,
                localId);
        return null;
    }

    /**
     * Creates file name in ECloud.
     *
     * @param path     path to file, if location is remote it must have correct URI syntax, otherwise it must be a proper path in a filesystem
     * @param version  version string, must be appropriate for the given record
     * @param recordId global record identifier
     */
    private String createFilename(String location, String path, String version, String recordId) {
        // when any of input parameter is null it is impossible to prepare filename
        if (location == null || path == null || version == null || recordId == null)
            return null;

        String mimeType = "";
        InputStream is = null;
        URI fullURI = null;
        try {
            if (resourceProvider.isLocal()) {
                try {
                    File localFile = new File(path);
                    fullURI = localFile.toURI();
                    mimeType = Files.probeContentType(localFile.toPath());
                } catch (IOException e) {
                    mimeType = mimeFromExtension(path);
                }
            } else {
                fullURI = new URI(path);
                mimeType = getContentType(fullURI.toURL());
            }
            if (mimeType == null)
                mimeType = mimeFromExtension(path);
            if (fullURI == null) {
                logger.error("URI for path {} could not be created.", path);
                return null;
            }
        } catch (URISyntaxException e) {
            logger.error(path + " is not correct URI", e);
            return null;
        } catch (IOException e) {
            logger.error("Problem with file: " + path, e);
            return null;
        }

        int retries = DEFAULT_RETRIES;
        while (retries-- > 0) {
            try {
                String fileName = resourceProvider.getFilename(location, resourceProvider.isLocal() ? path : fullURI.toString());

                // when there is a file processor specified run processing
                if (fileProcessor != null) {
                    File processed = fileProcessor.process(fullURI);
                    if (processed != null) {
                        processed.deleteOnExit();
                        is = new FileInputStream(processed);
                        // change filename extension to the one that processed file has
                        fileName = changeExtension(fileName, processed.getName());
                        mimeType = mimeFromExtension(fileName);
                    } else {
                        logger.error("Problem with processing file: {}", path);
                        return null;
                    }
                } else
                    is = fullURI.toURL().openStream();

                if (logger.isDebugEnabled())
                    logger.debug("Trying to upload file with name: {}", fileName);

                // path should contain proper slash characters ("/")
                URI result = fsc.uploadFile(recordId, resourceProvider.getRepresentationName(), version, fileName, is, mimeType);
                // operations below replace the filename got from File Service Client to the original filename, because the FSC returns encoded values of / and diacritic characters
                if (result != null) {
                    String fileURL = result.toString();
                    int i = fileURL.indexOf(FILES_PART);
                    // when there is no "files" part of the URL return the originally retrieved one
                    if (i == -1)
                        return fileURL;
                    // remove the filename part
                    fileURL = fileURL.substring(0, i + FILES_PART.length() + 1);
                    // add original filename
                    fileURL += fileName;
                    return fileURL;
                }
            } catch (ProcessingException e) {
                logger.warn("Processing HTTP request failed. Upload file: {} for record id: {}. Retries left: {}",
                        resourceProvider.getFilename(location, fullURI.toString()),
                        recordId,
                        retries
                );
            } catch (SocketTimeoutException e) {
                logger.warn("Read time out. Upload file: {} for record id: {}. Retries left: {}",
                        resourceProvider.getFilename(location, fullURI.toString()),
                        recordId,
                        retries);
            } catch (ConnectException e) {
                logger.error("Connection timeout. Upload file: {} for record id: {}. Retries left: {}",
                        resourceProvider.getFilename(location, fullURI.toString()),
                        recordId,
                        retries);
            } catch (FileNotFoundException e) {
                logger.error("Could not open input stream to file!", e);
            } catch (IOException e) {
                logger.error("Problem with detecting mime type from file (" + path + ")", e);
            } catch (Exception e) {
                logger.error("ECloud error when uploading file.", e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    logger.warn("Could not close stream.", e);
                }
            }
        }
        logger.warn("All attempts to upload file failed. Location: {} Path: {} Version: {} RecordId: {}",
                location,
                path,
                version,
                recordId);
        return null;
    }

    private String changeExtension(String fileName, String newFileName) {
        if (fileName == null || newFileName == null)
            return null;

        int i = fileName.lastIndexOf('.');
        int j = newFileName.lastIndexOf('.');
        if (i > 0 && j > 0)
            return fileName.substring(0, i) + newFileName.substring(j, newFileName.length());

        // when any of the extension does not exist return unchanged original fileName
        return fileName;
    }


    /**
     * Http HEAD Method to get URL content type
     *
     * @param url
     * @return content type
     * @throws IOException
     */
    private String getContentType(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("HEAD");
        if (isRedirect(connection.getResponseCode())) {
            String newUrl = connection.getHeaderField("Location"); // get redirect url from "location" header field
            logger.warn("Original request URL: {} redirected to: {}", url, newUrl);
            return getContentType(new URL(newUrl));
        }
        return connection.getContentType();
    }

    /**
     * Check status code for redirects
     *
     * @param statusCode
     * @return true if matched redirect group
     */
    private boolean isRedirect(int statusCode) {
        return (statusCode != HttpURLConnection.HTTP_OK && (statusCode == HttpURLConnection.HTTP_MOVED_TEMP
                || statusCode == HttpURLConnection.HTTP_MOVED_PERM
                || statusCode == HttpURLConnection.HTTP_SEE_OTHER));
    }

    /**
     * Detects mime type according to the file extension.
     *
     * @param path
     * @return
     */
    private String mimeFromExtension(String path) {
        int i = path.lastIndexOf(".");
        if (i == -1)
            return "application/octet-stream";
        return mimeTypes.get(path.substring(i + 1));
    }

    private String getProviderId(String providerId) {
        String mapped = dataProvidersMapping.get(providerId);
        if (mapped == null)
            return providerId;
        return mapped;
    }

    private synchronized String createProvider(String path) {
        // get mapped data provider identifier
        String providerId = getProviderId(resourceProvider.getDataProviderId(path));
        DataProviderProperties providerProps = resourceProvider.getDataProviderProperties(path);

        int retries = DEFAULT_RETRIES;
        while (retries-- > 0) {
            try {
                uis.createProvider(providerId, providerProps);
                return providerId;
            } catch (ProcessingException e) {
                logger.warn("Error processing HTTP request while creating provider {}. Retries left: {}",
                        providerId,
                        retries,
                        e);
            } catch (CloudException e) {
                if (e.getCause() instanceof ProviderAlreadyExistsException) {
                    logger.warn("Provider {} already exists.", providerId);
                    return providerId;
                }
                logger.error("Exception when creating provider occured.", e);
            } catch (Exception e) {
                logger.error("Exception when creating provider occured.", e);
            }
        }
        logger.warn("All attempts to create data provider failed. Provider: {} Path: {}", providerId, path);
        return null;
    }

    private void removeProcessedPaths(String providerId, FilePaths paths) {
        try {
            List<String> processed = new ArrayList<String>();
            for (String line : Files.readAllLines(FileSystems.getDefault().getPath(".", providerId + ResourceMigrator.TEXT_EXTENSION), StandardCharsets.UTF_8)) {
                StringTokenizer st = new StringTokenizer(line, ";");
                if (st.hasMoreTokens()) {
                    processed.add(st.nextToken());
                }
            }
            paths.removeAll(processed);
        } catch (IOException e) {
            logger.warn("Progress file for provider {} could not be opened. Returning all paths.", providerId);
        }
    }


    private boolean processProvider(String resourceProviderId, FilePaths providerPaths) {
        // first remove already processed paths, if there is no progress file for the provider no filtering is performed
        removeProcessedPaths(providerPaths.getIdentifier() != null ? providerPaths.getIdentifier() : resourceProviderId, providerPaths);

        boolean result = true;

        try {
            if (providerPaths.size() > 0) {
                String dataProviderId = retrieveDataProviderId(providerPaths);
                if (dataProviderId == null) {
                    logger.error("Cannot determine data provider.");
                    return false;
                }

                // first create provider, pass the path to the possible properties file, use first path to determine data provider id
                String propsFile = providerPaths.getLocation();
                if (!propsFile.endsWith(dataProviderId))
                    propsFile += LINUX_SEPARATOR + dataProviderId;
                propsFile += LINUX_SEPARATOR + dataProviderId + DefaultResourceProvider.PROPERTIES_EXTENSION;
                if (createProvider(propsFile) == null) {
                    // when create provider was not successful finish processing
                    return false;
                }

                List<String> duplicates = new ArrayList<String>();
                result &= processPaths(providerPaths, false, duplicates);
                if (duplicates.size() > 0) {
                    FilePaths duplicatePaths = new FilePaths(providerPaths.getLocation(), providerPaths.getDataProvider());
                    duplicatePaths.setIdentifier(providerPaths.getIdentifier());
                    duplicatePaths.getFullPaths().addAll(duplicates);
                    result &= processPaths(duplicatePaths, true, null);
                }
            }
        } catch (Exception e) {
            // when any uncaught exception occurs just catch it and report false result
            return false;
        }
        return result;
    }

    private String retrieveDataProviderId(FilePaths providerPaths) {
        String path = null;

        BufferedReader reader = providerPaths.getPathsReader();
        if (reader != null) {
            try {
                path = reader.readLine();
            } catch (IOException e) {
                logger.error("{}. Because of: {}", e.getMessage(), e.getCause());
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.error("{}. Because of: {}", e.getMessage(), e.getCause());
                }
            }
        } else
            path = providerPaths.size() > 0 ? providerPaths.getFullPaths().get(0) : null;
        if (path == null)
            return null;
        return resourceProvider.getDataProviderId(path);
    }

    private boolean processPaths(FilePaths fp, boolean duplicate, List<String> duplicates) throws IOException {
        if (fp == null)
            return false;

        // Resource provider identifier
        String resourceProviderId = fp.getDataProvider();

        // Location of the files
        String location = fp.getLocation();

        // Identifier of the file paths list
        String identifier = fp.getIdentifier();

        // key is local identifier, value is cloud identifier
        Map<String, String> cloudIds = new HashMap<>();
        // key is local identifier, value is version identifier
        Map<String, String> versionIds = new HashMap<>();
        // key is version identifier, value is a list of strings containing path=URI
        Map<String, List<String>> processed = new HashMap<>();
        // key is local identifier, value is files that were added
        Map<String, Integer> fileCount = new HashMap<>();

        int counter = 0;
        int errors = 0;

        int size = fp.size();

        if (size == 0)
            return false;

        BufferedReader reader = fp.getPathsReader();

        String prevLocalId = null;
        try {
            String path;
            for (; ; ) {
                if ((int) (((float) (counter) / (float) size) * 100) > (int) (((float) (counter - 1) / (float) size) * 100))
                    logger.info("Resource provider: {}. Progress: {} of {} ({}%). Errors: {}}. Duplicates: {}",
                            resourceProviderId,
                            counter,
                            size,
                            (int) (((float) (counter) / (float) size) * 100),
                            errors,
                            duplicate
                    );
                if (reader == null) {
                    // paths in list
                    if (counter >= size)
                        break;
                    path = fp.getFullPaths().get(counter);
                } else {
                    path = reader.readLine();
                    if (path == null)
                        break;
                }
                counter++;
                // get local record identifier
                String localId = resourceProvider.getLocalIdentifier(location, path, duplicate);
                if (localId == null) {
                    if (!duplicate) {
                        // check whether there is a duplicate record for the path and store the path for later use if so
                        if (resourceProvider.getLocalIdentifier(location, path, true) != null)
                            duplicates.add(path);
                    }
                    // when local identifier is null it means that the path may be wrong
                    logger.warn("Local identifier for path: {} could not be obtained. Skipping path...", path);
                    continue;
                }

                if (!duplicate) {
                    // check whether there is a duplicate record for the path and store the path for later use if so
                    if (resourceProvider.getLocalIdentifier(location, path, true) != null)
                        duplicates.add(path);
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Local identifier for path: {}: {}",path, localId);
                }
                if (!localId.equals(prevLocalId)) {
                    if (prevLocalId != null && cloudIds.get(prevLocalId) != null && versionIds.get(prevLocalId) != null && resourceProvider.getFileCount(prevLocalId) == fileCount.get(prevLocalId)) {
                        if (logger.isDebugEnabled())
                            logger.debug("Record {} complete. Saving...", prevLocalId);
                        // persist previous version if it was created
                        URI persistent = persistVersion(cloudIds.get(prevLocalId), versionIds.get(prevLocalId));
                        if (persistent != null) {
                            if (!permitVersion(cloudIds.get(prevLocalId), versionIds.get(prevLocalId)))
                                logger.warn("Could not grant permissions to version {} of record {}. Version is only available for current user.",
                                        versionIds.get(prevLocalId),
                                        cloudIds.get(prevLocalId)
                                );
                            saveProgress(fp.getIdentifier() != null ? fp.getIdentifier() : resourceProviderId, processed.get(versionIds.get(prevLocalId)), false, null);
                            // remove already saved paths
                            processed.get(versionIds.get(prevLocalId)).clear();
                            processed.remove(versionIds.get(prevLocalId));
                            cloudIds.remove(prevLocalId);
                            versionIds.remove(prevLocalId);
                        }
                    }
                    prevLocalId = localId;
                }
                if (cloudIds.get(localId) == null) {
                    // create new record when it was not created before
                    String cloudId = createRecord(resourceProvider.getDataProviderId(path), localId);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Local identifier for path: {}: {}",path, localId);
                    }
                    if (cloudId == null) {
                        // this is an error
                        errors++;
                        continue; // skip this path
                    }
                    cloudIds.put(localId, cloudId);
                    // create representation for the record
                    URI uri = createRepresentationName(resourceProvider.getDataProviderId(path), cloudId);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Representation for path: {}}: {}",
                                path,
                                uri);
                    }
                    if (uri == null) {
                        // this is not an error, version is already there and is persistent
                        continue; // skip this path
                    }
                    String verId = getVersionIdentifier(uri);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Version identifier for path: {}: {}", path, verId);
                    }
                    if (verId == null) {
                        // this is an error, version identifier could not be retrieved from representation URI
                        errors++;
                        continue; // skip this path
                    }
                    versionIds.put(localId, verId);
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Before creating file: \nLocation: {}\nPath: {}\nVersion: {}\nCloudId: {}",
                            location,
                            path,
                            versionIds.get(localId),
                            cloudIds.get(localId)
                    );
                }

                // create file name in ECloud with the specified name
                String fileAdded = createFilename(location, path, versionIds.get(localId), cloudIds.get(localId));
                if (fileAdded == null) {
                    // this is an error, upload failed
                    prevLocalId = null; // this should prevent persisting the version and saving progress
                    continue; // skip this path
                }
                // put the created URI for path to processed list
                if (processed.get(versionIds.get(localId)) == null)
                    processed.put(versionIds.get(localId), new ArrayList<>());
                processed.get(versionIds.get(localId)).add(path + ";" + fileAdded);
                // increase file count or set to 1 if it's a first file
                fileCount.put(localId, fileCount.get(localId) != null ? (fileCount.get(localId) + 1) : 1);
            }
            if (prevLocalId != null && resourceProvider.getFileCount(prevLocalId) == fileCount.get(prevLocalId)) {
                if (logger.isDebugEnabled())
                    logger.debug("Record {} complete. Saving...", prevLocalId);
                // persist previous version
                URI persistent = persistVersion(cloudIds.get(prevLocalId), versionIds.get(prevLocalId));
                if (persistent != null) {
                    if (!permitVersion(cloudIds.get(prevLocalId), versionIds.get(prevLocalId)))
                        logger.warn("Could not grant permissions to version {} of record {}. Version is only available for current user.",
                                versionIds.get(prevLocalId),
                                cloudIds.get(prevLocalId));
                    saveProgress(fp.getIdentifier() != null ? fp.getIdentifier() : resourceProviderId, processed.get(versionIds.get(prevLocalId)), false, null);
                    processed.get(versionIds.get(prevLocalId)).clear();
                    processed.remove(versionIds.get(prevLocalId));
                    cloudIds.remove(prevLocalId);
                    versionIds.remove(prevLocalId);
                }
            }
        } finally {
            if (reader != null)
                reader.close();
        }
        if (errors > 0)
            logger.warn("Migration of {} encountered {} errors.",
                    resourceProviderId,
                    errors);
        return (counter - errors) == size;
    }

    private void saveProgress(String providerId, List<String> strings, boolean truncate, String prefix) {
        try {
            Path dest = FileSystems.getDefault().getPath(".", (prefix != null ? prefix : "") + providerId + ResourceMigrator.TEXT_EXTENSION);
            if (truncate) {
                // make copy
                Path bkp = FileSystems.getDefault().getPath(".", (prefix != null ? prefix : "") + providerId + ".bkp");
                int c = 0;
                while (Files.exists(bkp))
                    bkp = FileSystems.getDefault().getPath(".", (prefix != null ? prefix : "") + providerId + ".bkp" + String.valueOf(c++));

                if (Files.exists(dest))
                    Files.copy(dest, bkp);
                // truncate and write to empty file
                Files.write(dest, strings, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } else
                Files.write(dest, strings, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        } catch (IOException e) {
            logger.error("Progress file " + (prefix != null ? prefix : "") + providerId + ".txt could not be saved.", e);
        }
    }

    private URI persistVersion(String cloudId, String version) {
        int retries = DEFAULT_RETRIES;
        while (retries-- > 0) {
            try {
                return mcs.persistRepresentation(cloudId, resourceProvider.getRepresentationName(), version);
            } catch (ProcessingException e) {
                logger.warn("Error processing HTTP request while persisting version: {} for record: {}. Retries left: {}",
                        version,
                        cloudId,
                        retries,
                        e);
            } catch (MCSException e) {
                logger.error("ECloud error when persisting version: {} for record: {}",
                        version,
                        cloudId,
                        e);
                if (e.getCause() instanceof ConnectException) {
                    logger.warn("Connection timeout error when persisting version: {} for record: {}. Retries left: {}",
                            version,
                            cloudId,
                            retries);
                } else if (e.getCause() instanceof SocketTimeoutException) {
                    logger.warn("Read time out error when persisting version: {} for record: {}. Retries left: {}",
                            version,
                            cloudId,
                            retries);
                } else
                    break;
            }
        }
        return null;
    }

    private boolean permitVersion(String cloudId, String version) {
        int retries = DEFAULT_RETRIES;
        while (retries-- > 0) {
            try {
                mcs.permitVersion(cloudId, resourceProvider.getRepresentationName(), version);
                return true;
            } catch (ProcessingException e) {
                logger.warn("Error processing HTTP request while granting permissions to version: " + version + " for record: " + cloudId + ". Retries left: " + retries, e);
            } catch (MCSException e) {
                logger.error("ECloud error when granting permissions to version: " + version + " for record: " + cloudId, e);
                if (e.getCause() instanceof ConnectException) {
                    logger.warn("Connection timeout error when granting permissions to version: {} for record: {}. Retries left: {}",
                            version, cloudId, retries);
                } else if (e.getCause() instanceof SocketTimeoutException) {
                    logger.warn("Read time out error when granting permissions to version: {} for record: {}. Retries left: {}",
                            version,
                            cloudId,
                            retries);
                } else
                    break;
            }
        }
        return false;
    }

    /*
    URI parameter is the version URI returned when creating representation name, it ends with a version identifier
     */
    private String getVersionIdentifier(URI uri) {
        String uriStr = uri.toString();
        int pos = uriStr.lastIndexOf("/");
        if (pos != -1)
            return uriStr.substring(pos + 1);
        return null;
    }

    public void verify() {
        // first scan locations to get the map of filenames
        long start = System.currentTimeMillis();
        // key is provider id, value is a list of files to add
        Map<String, List<FilePaths>> paths = resourceProvider.scan();
        logger.info("Scanning resource provider locations finished in {} sec.",
                ((float) (System.currentTimeMillis() - start) / (float) 1000));

        List<Future<VerificationResult>> results = null;
        List<Callable<VerificationResult>> tasks = new ArrayList<>(paths.size());

        // create task for each resource provider
        Iterator<Entry<String, List<FilePaths>>> iterator = paths.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, List<FilePaths>> entry = iterator.next();
            logger.info("Starting verification task thread for provider {}...", entry.getKey());
            tasks.add(new ProviderVerifier(entry.getKey(), entry.getValue(), null));
        }

        if (tasks.isEmpty())
            return;

        try {
            // invoke a separate thread for each provider
            results = threadPool.invokeAll(tasks);

            VerificationResult providerResult;
            for (Future<VerificationResult> result : results) {
                providerResult = result.get();
                logger.info("Verification of provider {} performed successfully. Verification time: {} sec. Number of not migrated files: {}",
                        providerResult.getProviderId(),
                        providerResult.getTime(),
                        providerResult.getNotMigratedCount()
                );
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Verification processed interrupted.", e);
        } catch (ExecutionException e) {
            logger.error("Problem with verification task thread execution.", e);
        }
    }


    private long verifyProvider(String resourceProviderId, FilePaths providerPaths) {
        BufferedReader reader = providerPaths.getPathsReader();

        String line = "";
        String localId = "";
        String cloudId;

        Map<String, String> cloudIds = new HashMap<>();
        List<String> strings = new ArrayList<>();
        Set<String> migratedLocalIds = new HashSet<>();
        Set<String> notExistingLocalIds = new HashSet<>();

        long count = 0;
        int counter = 0;
        int total = providerPaths.size();
        // Identifier of the file paths list

        try {
            for (; ; ) {
                try {
                    if ((int) (((float) (counter) / (float) total) * 100) > (int) (((float) (counter - 1) / (float) total) * 100))
                        logger.info("Resource provider: {}. Progress: {} of {} ({}%).",
                                resourceProviderId,
                                counter,
                                total,
                                (int) (((float) (counter) / (float) total) * 100));
                    if (reader == null) {
                        // paths in list
                        if (counter >= total)
                            break;
                        line = providerPaths.getFullPaths().get(counter);
                    } else {
                        line = reader.readLine();
                        if (line == null)
                            break;
                    }
                    counter++;

                    localId = resourceProvider.getLocalIdentifier(providerPaths.getLocation(), line, false);

                    if (localId == null) {
                        strings.add(line + " (no local id)");
                        continue;
                    }
                    // there is no sense in checking other files from the record if it has the persistent representation
                    if (migratedLocalIds.contains(localId))
                        continue;

                    if (cloudIds.get(localId) == null) {
                        if (notExistingLocalIds.contains(localId))
                            continue;

                        cloudId = getRecord(resourceProvider.getDataProviderId(line), localId);
                        if (cloudId == null) {
                            strings.add(line + " (upload " + localId + ")");
                            notExistingLocalIds.add(localId.intern());
                            count += resourceProvider.getFileCount(localId) - 1;
                            continue;
                        }
                        cloudIds.put(localId, cloudId);
                    }

                    List<Representation> representations = mcs.getRepresentations(cloudIds.get(localId), resourceProvider.getRepresentationName());
                    if (representations == null || representations.size() == 0) {
                        strings.add(line.intern());
                        continue;
                    }
                    boolean persistent = false;
                    int fileCount = 0;
                    for (Representation representation : representations) {
                        if (representation.isPersistent()) {
                            persistent = true;
                            break;
                        } else {
                            int size = representation.getFiles().size();
                            if (size > fileCount)
                                fileCount = size;
                        }
                    }
                    if (!persistent) {
                        // if file count for this record is the same as determined from resourceProvider then it means that the data was migrated but for some reason the record was not persisted
                        if (fileCount == resourceProvider.getFileCount(localId))
                            strings.add(line + " (persist " + localId + ")");
                        else
                            strings.add(line + " (upload " + localId + ")");
                        count += resourceProvider.getFileCount(localId) - 1;
                    }
                    // when we got here it means that for the current localId there is a cloud id and persistent representation - the whole record is migrated
                    migratedLocalIds.add(localId.intern());
                } catch (IOException e) {
                    logger.error("{}.Because of: {}", e.getMessage(), e.getCause());
                    break;
                } catch (RecordNotExistsException | RepresentationNotExistsException e) {
                    strings.add(line + " (upload " + localId + ")");
                } catch (MCSException e) {
                    logger.error("Problem with getting representation.");
                    logger.error("{}.Because of: {}", e.getMessage(), e.getCause());

                }
            }
            saveProgress(providerPaths.getIdentifier() != null ? providerPaths.getIdentifier() : resourceProviderId, strings, true, "verify_");
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.error("{}.Because of: {}", e.getMessage(), e.getCause());
                }
            }
        }
        return strings.size() + count;
    }

    private class ProviderMigrator implements Callable<MigrationResult> {
        // resource provider identifier
        private String providerId;

        // paths to files that will be migrated
        private List<FilePaths> paths;

        // identifier of part of file paths (usually a name of the directory), may be null
        private String identifier;

        // Pool of threads used to migrate files
        private final ExecutorService threadProviderPool = Executors
                .newFixedThreadPool(threadsCount);

        ProviderMigrator(String providerId, List<FilePaths> paths, String identifier) {
            this.providerId = providerId;
            this.paths = paths;
            this.identifier = identifier;
        }

        public MigrationResult call()
                throws Exception {
            long start = System.currentTimeMillis();
            boolean success = true;

            List<FilePaths> split = resourceProvider.split(paths);
            if (split.equals(paths)) {
                // when split operation did not change anything just run the migration for the given paths
                for (FilePaths fp : paths)
                    success &= processProvider(providerId, fp);
            } else { // initial paths were split into more sets, for each set run separate thread and gather results
                List<Future<MigrationResult>> results = null;
                List<Callable<MigrationResult>> tasks = new ArrayList<>(split.size());

                boolean mergeProgress = false;

                // create task for each file path
                for (FilePaths fp : split) {
                    logger.info("Starting task thread for file paths " + fp.getIdentifier() + "...");
                    List<FilePaths> lst = new ArrayList<>();
                    lst.add(fp);
                    mergeProgress |= !fp.getIdentifier().equals(fp.getDataProvider());
                    tasks.add(new ProviderMigrator(providerId, lst, fp.getIdentifier()));
                }

                try {
                    // invoke a separate thread for each provider
                    results = threadProviderPool.invokeAll(tasks);

                    MigrationResult partResult;
                    for (Future<MigrationResult> result : results) {
                        partResult = result.get();
                        logger.info("Migration of part {} ({}) performed. Migration time: {} sec.",
                                partResult.getIdentifier(),
                                partResult.getProviderId(),
                                partResult.getTime()
                        );
                        success &= partResult.isSuccessful();
                    }
                    // concatenate progress files to one if necessary
                    if (mergeProgress)
                        saveProgressFromThreads(providerId, split);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Migration processed interrupted.", e);
                } catch (ExecutionException e) {
                    logger.error("Problem with migration task thread execution.", e);
                }

            }

            return new MigrationResult(success, providerId, (float) (System.currentTimeMillis() - start) / (float) 1000, identifier);
        }
    }

    private void saveProgressFromThreads(String providerId, List<FilePaths> paths) {
        BufferedReader reader = null;

        try {
            // new progress file with provider name
            Path dest = FileSystems.getDefault().getPath(".", providerId + ResourceMigrator.TEXT_EXTENSION);
            if (Files.exists(dest)) {
                // make copy
                Path bkp = FileSystems.getDefault().getPath(".", providerId + ".bkp");
                int c = 0;
                while (Files.exists(bkp))
                    bkp = FileSystems.getDefault().getPath(".", providerId + ".bkp" + String.valueOf(c++));

                Files.copy(dest, bkp);
                Files.delete(dest);
            }

            // read every file for a paths object and append it to the destination file
            for (FilePaths fp : paths) {
                if (fp.getIdentifier() == null || fp.getIdentifier().equals(providerId))
                    continue;
                Path progressFile = FileSystems.getDefault().getPath(".", fp.getIdentifier() + ResourceMigrator.TEXT_EXTENSION);
                try {
                    reader = Files.newBufferedReader(progressFile, StandardCharsets.UTF_8);
                    for (; ; ) {
                        String line = reader.readLine();
                        if (line == null)
                            break;
                        if (!line.endsWith("\n"))
                            line += "\n";
                        Files.write(dest, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                    }
                } catch (IOException e) {
                    // do nothing, move to the next file
                    logger.warn("Problem with file {}", progressFile.toAbsolutePath());
                } finally {
                    if (reader != null)
                        reader.close();
                }
            }
        } catch (IOException e) {
            logger.error("Progress file " + providerId + ".txt could not be saved.", e);
        }
    }

    private class MigrationResult {
        // success indicator
        private Boolean success;

        // resource provider identifier
        private String providerId;

        // Part identifier
        private String identifier;

        // execution time
        private float time;

        MigrationResult(Boolean success, String providerId, float time, String identifier) {
            this.success = success;
            this.providerId = providerId;
            this.time = time;
            this.identifier = identifier;
        }

        String getProviderId() {
            return providerId;
        }

        Boolean isSuccessful() {
            return success;
        }

        float getTime() {
            return time;
        }

        String getIdentifier() {
            return identifier;
        }
    }


    private class ProviderVerifier implements Callable<VerificationResult> {
        // resource provider identifier
        private String providerId;

        // paths to files that will be migrated
        private List<FilePaths> paths;

        // identifier of part of file paths (usually a name of the directory), may be null
        private String identifier;

        // Pool of threads used to migrate files
        private final ExecutorService threadProviderPool = Executors
                .newFixedThreadPool(threadsCount);

        ProviderVerifier(String providerId, List<FilePaths> paths, String identifier) {
            this.providerId = providerId;
            this.paths = paths;
            this.identifier = identifier;
        }

        public VerificationResult call()
                throws Exception {
            long start = System.currentTimeMillis();

            long notMigrated = 0;

            List<FilePaths> split = resourceProvider.split(paths);
            if (split.equals(paths)) {
                // when split operation did not change anything just run the verification for the given paths
                for (FilePaths fp : paths)
                    notMigrated += verifyProvider(providerId, fp);
            } else { // initial paths were split into more sets, for each set run separate thread and gather results
                List<Future<VerificationResult>> results = null;
                List<Callable<VerificationResult>> tasks = new ArrayList<>(split.size());

                // create task for each file path
                for (FilePaths fp : split) {
                    logger.info("Starting verification task thread for file paths " + fp.getIdentifier() + "...");
                    List<FilePaths> lst = new ArrayList<>();
                    lst.add(fp);
                    tasks.add(new ProviderVerifier(providerId, lst, fp.getIdentifier()));
                }

                try {
                    // invoke a separate thread for each provider
                    results = threadProviderPool.invokeAll(tasks);

                    VerificationResult partResult;
                    for (Future<VerificationResult> result : results) {
                        partResult = result.get();
                        logger.info("Verification of part {} ({}) performed successfully. Verification time: {}sec. Number of not migrated files: {}",
                                partResult.getIdentifier(),
                                partResult.getProviderId(),
                                partResult.getTime(),
                                partResult.getNotMigratedCount()
                        );

                        notMigrated += partResult.getNotMigratedCount();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Verification processed interrupted.", e);
                } catch (ExecutionException e) {
                    logger.error("Problem with verification task thread execution.", e);
                }

            }

            return new VerificationResult(notMigrated, providerId, (float) (System.currentTimeMillis() - start) / (float) 1000, identifier);
        }
    }

    private class VerificationResult {
        // resource provider identifier
        private String providerId;

        // Part identifier
        private String identifier;

        // execution time
        private float time;

        // number of not migrated files
        private long notMigrated;

        VerificationResult(long notMigrated, String providerId, float time, String identifier) {
            this.notMigrated = notMigrated;
            this.providerId = providerId;
            this.time = time;
            this.identifier = identifier;
        }

        String getProviderId() {
            return providerId;
        }

        long getNotMigratedCount() {
            return notMigrated;
        }

        float getTime() {
            return time;
        }

        String getIdentifier() {
            return identifier;
        }
    }

    private class LocalIdVerifier implements Callable<LocalIdVerificationResult> {
        // paths to files that will be migrated
        private List<String> localIds;

        // identifier of part of file paths (usually a name of the directory), may be null
        private String identifier;

        LocalIdVerifier(List<String> localIds, String identifier) {
            this.localIds = localIds;
            this.identifier = identifier;
        }

        public LocalIdVerificationResult call()
                throws Exception {
            long start = System.currentTimeMillis();

            long notMigrated = 0;
            int counter = 0;

            int total = localIds.size();
            String localId;
            String cloudId;
            List<String> strings = new ArrayList<>();

            // truncate file
            saveProgress(resourceProvider.getDataProviderId(""), strings, true, "verifylocalids_" + identifier + "_");

            for (Iterator<String> i = localIds.iterator(); i.hasNext(); ) {
                if ((int) (((float) (counter) / (float) total) * 100) > (int) (((float) (counter - 1) / (float) total) * 100)) {
                    logger.info("Local identifiers verification part {} progress: {} of {} ({}%).",
                            identifier,
                            counter,
                            total,
                            (int) (((float) (counter) / (float) total) * 100)
                    );
                    if (!strings.isEmpty()) {
                        saveProgress(resourceProvider.getDataProviderId(""), strings, false, "verifylocalids_" + identifier + "_");
                        notMigrated += strings.size();
                        strings.clear();
                    }
                }
                counter++;

                localId = i.next();

                cloudId = getRecord(resourceProvider.getDataProviderId(""), localId);
                if (cloudId == null) {
                    String path = getPathFromMapping(resourceProvider.getReversedMapping(), localId);
                    strings.add(localId + ";" + path);
                } else {
                    List<Representation> representations = null;
                    try {
                        representations = mcs.getRepresentations(cloudId, resourceProvider.getRepresentationName());
                    } catch (MCSException e) {
                        logger.error("{}. Because of: {}", e.getMessage(), e.getCause());
                    }
                    if (representations == null || representations.isEmpty()) {
                        strings.add(localId + ";" + "no representation");
                        continue;
                    }
                    boolean persistent = false;
                    int fileCount = 0;
                    for (Representation representation : representations) {
                        if (representation.isPersistent()) {
                            persistent = true;
                            break;
                        } else {
                            int size = representation.getFiles().size();
                            if (size > fileCount)
                                fileCount = size;
                        }
                    }
                    if (!persistent) {
                        // if file count for this record is the same as determined from resourceProvider then it means that the data was migrated but for some reason the record was not persisted
                        if (fileCount == resourceProvider.getFileCount(localId))
                            strings.add(localId + ";no persistent representation");
                        else
                            strings.add(localId + ";representation incomplete");
                    }
                }
            }
            if (!strings.isEmpty()) {
                saveProgress(resourceProvider.getDataProviderId(""), strings, false, "verifylocalids_" + identifier + "_");
                notMigrated += strings.size();
            }
            return new LocalIdVerificationResult(notMigrated, (float) (System.currentTimeMillis() - start) / (float) 1000, identifier);
        }
    }

    private class LocalIdVerificationResult {
        // Part identifier
        private String identifier;

        // execution time
        private float time;

        // number of not migrated files
        private long notMigrated;

        LocalIdVerificationResult(long notMigrated, float time, String identifier) {
            this.notMigrated = notMigrated;
            this.time = time;
            this.identifier = identifier;
        }

        long getNotMigratedCount() {
            return notMigrated;
        }

        float getTime() {
            return time;
        }

        String getIdentifier() {
            return identifier;
        }
    }


    public void clean(String providerId) {
        try {
            List<String> toSave = new ArrayList<>();

            new Cleaner().cleanRecords(providerId, mcs, uis);

            for (String line : Files.readAllLines(FileSystems.getDefault().getPath(".", providerId + ResourceMigrator.TEXT_EXTENSION), StandardCharsets.UTF_8)) {
                StringTokenizer st = new StringTokenizer(line, ";");
                if (st.hasMoreTokens()) {
                    st.nextToken();
                }
                String url = st.nextToken();
                int pos = url.indexOf("/records/");
                if (pos > -1) {
                    String id = url.substring(pos + "/records/".length());
                    id = id.substring(0, id.indexOf("/"));
                    int retries = DEFAULT_RETRIES;
                    while (retries-- > 0) {
                        try {
                            mcs.deleteRecord(id);
                            uis.deleteCloudId(id);
                            break;
                        } catch (ProcessingException e) {
                            logger.warn("Error processing HTTP request while deleting record: " + id + ". Retries left: " + retries, e);
                            if (retries == 0) // when this is the last unsuccessful try
                                toSave.add(line);
                        } catch (RecordNotExistsException e) {
                            // no record, no problem
                            break;
                        } catch (Exception e) {
                            logger.warn("Could not delete record.", e);
                            if (retries == 0) // when this is the last unsuccessful try
                                toSave.add(line);
                        }
                    }
                }
            }
            saveProgress(providerId, toSave, true, null);
        } catch (IOException e) {
            logger.error("Problem with file.", e);
        }
    }


    private class PublicAccessGranter implements Callable<PublicAccessGranterResult> {
        // local identifiers of records to be checked and granted public access
        private List<String> localIds;

        // identifier of part of local identifiers (usually a name of the directory), may be null
        private String identifier;

        private boolean simulate;

        PublicAccessGranter(List<String> localIds, String identifier, boolean simulate) {
            this.localIds = localIds;
            this.identifier = identifier;
            this.simulate = simulate;
        }

        public PublicAccessGranterResult call()
                throws Exception {
            long start = System.currentTimeMillis();

            long notGranted = 0;
            int counter = 0;

            int total = localIds.size();
            String cloudId;
            List<String> strings = new ArrayList<>();

            // truncate file
            saveProgress(resourceProvider.getDataProviderId(""), strings, true, "grantaccess_" + identifier + "_");

            for (String localId : localIds) {
                if ((int) (((float) (counter) / (float) total) * 100) > (int) (((float) (counter - 1) / (float) total) * 100)) {
                    logger.info("Granting public access part {} progress: {} of {} ({}%).",
                            identifier,
                            counter,
                            total,
                            (int) (((float) (counter) / (float) total) * 100)
                    );
                    if (!strings.isEmpty()) {
                        saveProgress(resourceProvider.getDataProviderId(""), strings, false, "grantaccess_" + identifier + "_");
                        notGranted += strings.size();
                        strings.clear();
                    }
                }
                counter++;

                cloudId = getRecord(resourceProvider.getDataProviderId(""), localId);
                if (cloudId != null) {
                    List<Representation> representations = null;
                    try {
                        representations = mcs.getRepresentations(cloudId, resourceProvider.getRepresentationName());
                    } catch (MCSException e) {
                        logger.warn(e.getMessage());
                    }
                    if (representations == null || representations.isEmpty()) {
                        continue;
                    }

                    boolean noPersistentVersion = true;

                    for (Representation representation : representations) {
                        if (representation.isPersistent()) {
                            noPersistentVersion = false;
                            if (!accessible(representation)) {
                                // in this case there are no permissions
                                if (!simulate) {
                                    mcs.permitVersion(cloudId, representation.getRepresentationName(), representation.getVersion());
                                }
                                strings.add(localId + ";no permissions for persistent version");
                                break;
                            }
                        }
                    }

                    if (noPersistentVersion) {
                        strings.add(localId + ";no persistent version");
                    }
                }
            }
            if (!strings.isEmpty()) {
                saveProgress(resourceProvider.getDataProviderId(""), strings, false, "grantaccess_" + identifier + "_");
                notGranted += strings.size();
            }
            return new PublicAccessGranterResult(notGranted, (float) (System.currentTimeMillis() - start) / (float) 1000, identifier);
        }

        private boolean accessible(Representation representation) throws IOException {
            URL url = new URL(representation.getAllVersionsUri() + "/" + representation.getVersion());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            int code = connection.getResponseCode();
            connection.disconnect();
            return (code != Response.Status.METHOD_NOT_ALLOWED.getStatusCode());
        }
    }

    private class PublicAccessGranterResult {
        // Part identifier
        private String identifier;

        // execution time
        private float time;

        // number of versions without public access
        private long notGranted;

        PublicAccessGranterResult(long notGranted, float time, String identifier) {
            this.notGranted = notGranted;
            this.time = time;
            this.identifier = identifier;
        }

        long getNotGrantedCount() {
            return notGranted;
        }

        float getTime() {
            return time;
        }

        String getIdentifier() {
            return identifier;
        }
    }
}
