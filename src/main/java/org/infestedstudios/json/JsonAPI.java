package org.infestedstudios.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.Setter;
import org.infestedstudios.json.storage.StorageMode;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A JSON API that provides methods for saving and loading data to and from JSON files.
 * The API supports caching data in memory and on disk, and provides methods for validating and transforming data before saving.
 */
public class JsonAPI {

    private static final Logger logger = Logger.getLogger(JsonAPI.class.getName());
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private final Gson gson;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private static String basePath = "./"; // Default base path

    @Setter
    @Getter
    private StorageMode storageMode = StorageMode.CACHE_THEN_DISK;

    @Setter
    @Getter
    private boolean debugMode = false;

    public JsonAPI(String basePath) {
        JsonAPI.basePath = ensureTrailingSeparator(basePath);
        this.gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation() // Exclude fields without @Expose annotation
                .create();
    }

    public static JsonAPI getInstance() {
        return new JsonAPI(basePath);
    }

    public <T> void store(String fileName, T data, StorageMode storageMode) {
        logDebug("Storing data to file: " + fileName + " with storage mode: " + storageMode);

        switch (storageMode) {
            case MEMORY_ONLY:
                cache.put(fileName, data);
                break;
            case DISK_ONLY:
                saveToFile(fileName, data);
                break;
            case CACHE_THEN_DISK:
                cache.put(fileName, data);
                saveAsync(fileName, data);
                break;
            default:
                throw new IllegalArgumentException("Unsupported storage mode: " + storageMode);
        }
    }

    public <T> void store(String key, T data, Predicate<T> validator, Function<T, T> transformBeforeStore, StorageMode storageMode) {
        logDebug("Storing data to file: " + key + " with storage mode: " + storageMode);

        if (validator.test(data)) {
            T transformedData = transformBeforeStore.apply(data);
            store(key, transformedData, storageMode);
        } else {
            throw new IllegalArgumentException("Data validation failed for key: " + key);
        }
    }

    public <T> void load(String fileName, Class<T> typeOfT, Consumer<T> callback, StorageMode storageMode) {
        logDebug("Loading data from file: " + fileName + " with storage mode: " + storageMode);

        switch (storageMode) {
            case MEMORY_ONLY:
                callback.accept(typeOfT.cast(cache.get(fileName)));
                break;
            case DISK_ONLY:
            case CACHE_THEN_DISK:
                if (cache.containsKey(fileName)) {
                    callback.accept(typeOfT.cast(cache.get(fileName)));
                } else {
                    loadAsync(fileName, typeOfT, callback);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported storage mode: " + storageMode);
        }
    }

    public <T> T load(String key, Type typeOfT, Function<T, T> transformAfterLoad, StorageMode storageMode) {
        logDebug("Loading data from file: " + key + " with storage mode: " + storageMode);
        T data = null;

        switch (storageMode) {
            case MEMORY_ONLY:
                data = (T) cache.get(key);
                break;
            case DISK_ONLY:
            case CACHE_THEN_DISK:
                if (cache.containsKey(key)) {
                    data = (T) cache.get(key);
                } else {
                    data = loadFromFile(key, typeOfT);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported storage mode: " + storageMode);
        }

        return transformAfterLoad.apply(data);
    }

    public void clearData(String fileName) {
        cache.remove(fileName);
        File file = new File(buildFilePath(fileName));
        if (file.exists() && file.delete()) {
            logDebug("Cleared data from file: " + fileName);
        }
    }

    private <T> void saveToFile(String fileName, T data) {
        try {
            String fullPath = buildFilePath(fileName);
            createFileIfNotExists(fullPath);

            try (FileWriter writer = new FileWriter(fullPath)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save data to file: " + fileName, e);
        }
    }

    public <T> void saveAsync(String fileName, T data) {
        logDebug("Saving Async data to file: " + fileName + " with storage mode: " + storageMode);

        executorService.execute(() -> saveToFile(fileName, data));
    }

    public <T> void loadAsync(String fileName, Type typeOfT, Consumer<T> callback) {
        logDebug("Loading Async data from file: " + fileName + " with storage mode: " + storageMode);

        executorService.execute(() -> {
            T data = loadFromFile(fileName, typeOfT);
            callback.accept(data);
        });
    }

    private <T> T loadFromFile(String fileName, Type typeOfT) {
        try {
            String fullPath = buildFilePath(fileName);
            if (Files.exists(Paths.get(fullPath))) {
                try (FileReader reader = new FileReader(fullPath)) {
                    return gson.fromJson(reader, typeOfT);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load data from file: " + fileName, e);
        }
        return null;
    }

    public void deleteAsync(String key) {
        logDebug("Deleting Async data for file: " + key);

        executorService.execute(() -> {
            String fullPath = buildFilePath(key);
            File file = new File(fullPath);
            if (file.exists() && !file.delete()) {
                logger.log(Level.WARNING, "Failed to delete file: " + fullPath);
            }
        });
    }

    public static <T> void saveVersion(String fileName, T jsonData) {
        try {
            String versionedFileName = fileName + "_" + dateFormat.format(new Date()) + ".json";
            String fullPath = basePath + "versions/" + versionedFileName;

            File versionDir = new File(basePath + "versions/");
            if (!versionDir.exists()) {
                versionDir.mkdirs();
            }

            Files.write(Paths.get(fullPath), (byte[]) jsonData);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save versioned data", e);
        }
    }

    public static void deleteVersion(String fileName) {
        try {
            File versionDir = new File(basePath + "versions/");
            if (versionDir.exists()) {
                for (File file : versionDir.listFiles()) {
                    if (file.getName().startsWith(fileName) && !file.delete()) {
                        logger.log(Level.WARNING, "Failed to delete versioned file: " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to delete versioned files", e);
        }
    }

    private static String ensureTrailingSeparator(String path) {
        return path.endsWith(File.separator) ? path : path + File.separator;
    }

    private static String buildFilePath(String fileName) {
        return basePath + fileName + ".json";
    }

    private static void createFileIfNotExists(String fullPath) throws IOException {
        File file = new File(fullPath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
    }

    private void logDebug(String message) {
        if (debugMode) {
            logger.log(Level.INFO, message);
        }
    }
}
