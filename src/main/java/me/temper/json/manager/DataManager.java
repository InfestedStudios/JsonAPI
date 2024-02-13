package me.temper.json.manager;

import com.google.gson.Gson;
import lombok.Setter;
import me.temper.json.async.AsyncFileOperations;
import me.temper.json.storage.StorageMode;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A data manager that saves data to memory, disk, or both.
 */
public class DataManager {
    private static final Gson gson = new Gson();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final Map<String, Object> cache = new ConcurrentHashMap<>();
    @Setter
    private static StorageMode storageMode = StorageMode.CACHE_THEN_DISK;
    private static AsyncFileOperations fileOps;
    private static File dataFolder;

    /**
     * Creates a new data manager with the specified data folder.
     *
     * @param dataFolder the folder where data will be stored
     */
    public DataManager(File dataFolder) {
        DataManager.dataFolder = dataFolder;
        fileOps = new AsyncFileOperations(dataFolder);
    }

    /**
     * Saves data to the data manager with the specified key.
     *
     * @param filePath the key to use for saving the data
     * @param data  the data to save
     * @param <T>   the type of data to save
     */
    public static <T> void store(String filePath, T data, StorageMode storageMode) {
        if (filePath == null || data == null || fileOps == null) {
            throw new IllegalArgumentException("FilePath, data, and fileOps must not be null");
        }
        switch (storageMode) {
            case MEMORY_ONLY:
                cache.put(filePath, data);
                break;
            case DISK_ONLY:
                fileOps.saveAsync(filePath, data);
                break;
            case CACHE_THEN_DISK:
                cache.put(filePath, data);
                fileOps.saveAsync(filePath, data);
                break;
        }
    }


    /**
     * Loads data from the data manager with the specified key.
     *
     * @param filePath    the key to use for loading the data
     * @param typeOfT     the type of data to load
     * @param <T>         the type of data to load
     * @return the loaded data, or null if no data was found
     */
    public static <T> T load(String filePath, Type typeOfT, StorageMode storageMode) {
        switch (storageMode) {
            case MEMORY_ONLY:
                return (T) cache.get(filePath);
            case DISK_ONLY:
            case CACHE_THEN_DISK:
                if (cache.containsKey(filePath)) {
                    return (T) cache.get(filePath);
                } else {
                    final T[] data = (T[]) new Object[1];
                    fileOps.loadAsync(filePath, typeOfT, result -> data[0] = (T) result);
                    return data[0];
                }
        }
        return null;
    }

    /**
     * Clears data from the data manager with the specified key.
     * Also deletes the file from disk if aysnc.
     *
     * @param filePath the key to use for clearing the data
     */
    public static void clearData(String filePath) {
        cache.remove(filePath);
        // Asynchronous file deletion
        fileOps.deleteAsync(filePath);
    }

    /**
     * Saves a version of the specified data to the data manager.
     *
     * @param fileName the name of the file
     * @param jsonData the JSON data to save
     */
    private static void saveVersion(String fileName, String jsonData) {
        try {
            String versionTimestamp = dateFormat.format(new Date());
            String versionedFileName = fileName + "_" + versionTimestamp + ".json";
            File versionDir = new File(dataFolder, "versions");
            if (!versionDir.exists()) {
                versionDir.mkdirs();
            }
            File versionFile = new File(versionDir, versionedFileName);
            Files.write(Paths.get(versionFile.toURI()), jsonData.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}