package me.temper.json;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import me.temper.json.storage.StorageMode;

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

/**
 * A JSON API that provides methods for saving and loading data to and from JSON files.
 * The API supports caching data in memory and on disk, and provides methods for validating and transforming data before saving.
 */
public class JsonAPI {

    private Gson gson = new Gson();
    /**
     * -- GETTER --
     *  Returns the current storage mode for the API.
     * -- SETTER --
     *  Sets the storage mode for the API.
     *
     * @param storageMode the storage mode to use

     */
    @Setter
    @Getter
    private StorageMode storageMode = StorageMode.CACHE_THEN_DISK;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private static String basePath = "./"; // Default base path


    public JsonAPI(String basePath) {
        this.basePath = basePath; // Set base path through constructor
        this.executorService = Executors.newCachedThreadPool();
    }


    /**
     * Returns the single instance of the JsonAPI.
     */
    public static JsonAPI getInstance() {
        return new JsonAPI(basePath);
    }

    /**
     * Saves data to the specified file name.
     *
     * @param fileName the name of the file to save to
     * @param data the data to save
     */
    public <T> void store(String fileName, T data) {
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
        }
    }

    /**
     * Saves data to the specified file name, applying a validation function and transformation function before saving.
     *
     * @param key the key to use for the data
     * @param data the data to save
     * @param validator a function that returns true if the data is valid, or false if it is invalid
     * @param transformBeforeStore a function that transforms the data before saving
     */
    public <T> void store(String key, T data, Predicate<T> validator, Function<T, T> transformBeforeStore) {
        if (validator.test(data)) {
            T transformedData = transformBeforeStore.apply(data);
            switch (storageMode) {
                case MEMORY_ONLY:
                    cache.put(key, transformedData);
                    break;
                case DISK_ONLY:
                    saveAsync(key, transformedData);
                    break;
                case CACHE_THEN_DISK:
                    cache.put(key, transformedData);
                    saveAsync(key, transformedData);
                    break;
            }
        } else {
            throw new IllegalArgumentException("Data validation failed");
        }
    }

    /**
     * Loads data from the specified file name, using the specified type.
     *
     * @param fileName the name of the file to load from
     * @param typeOfT the type of data to load
     * @param callback a function that is called with the loaded data
     */
    public <T> void load(String fileName, Class<T> typeOfT, java.util.function.Consumer<T> callback) {
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
        }
    }

    /**
     * Loads data from the specified file name, using the specified type, and applies a transformation function to the data after loading.
     *
     * @param key the key to use for the data
     * @param typeOfT the type of data to load
     * @param transformAfterLoad a function that transforms the data after loading
     * @return the loaded data
     */
    public <T> T load(String key, Type typeOfT, Function<T, T> transformAfterLoad) {
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
                    final T[] tempData = (T[]) new Object[1];
                    loadAsync(key, typeOfT, result -> tempData[0] = (T) result);
                    data = tempData[0];
                }
                break;
        }
        return transformAfterLoad.apply(data);
    }

    /**
     * Clears the data for the specified file name.
     *
     * @param fileName the name of the file for which to clear data
     */
    public void clearData(String fileName) {
        cache.remove(fileName);
        File file = new File(fileName + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Saves data to a file, using the Gson library for JSON serialization.
     *
     * @param fileName the name of the file to save to
     * @param data the data to save
     */
    private <T> void saveToFile(String fileName, T data) {
        try {
            String fullPath = basePath + fileName + ".json"; // Construct full path
            File file = new File(fullPath);
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    /**
     * Saves data to a file asynchronously.
     *
     * @param <T>        the type of data to be saved
     * @param fileName   the name of the file to be saved to
     * @param data       the data to be saved
     */
    public <T> void saveAsync(String fileName, T data) {
        executorService.execute(() -> {
            try {
                String fullPath = basePath + fileName + ".json"; // Construct full path
                File file = new File(fullPath);
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                try (FileWriter writer = new FileWriter(file)) {
                    gson.toJson(data, writer);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Loads data from a file asynchronously.
     *
     * @param <T>           the type of data to be loaded
     * @param fileName      the name of the file to be loaded from
     * @param typeOfT       the type of the data to be loaded
     * @param callback      the callback to be invoked with the loaded data
     */
    public <T> void loadAsync(String fileName, Type typeOfT, Consumer<T> callback) {
        executorService.execute(() -> {
            T data = null;
            try {
                String fullPath = basePath + fileName + ".json"; // Construct full path
                File file = new File(fullPath);
                if (file.exists()) {
                    try (FileReader reader = new FileReader(file)) {
                        data = gson.fromJson(reader, typeOfT);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            T finalData = data;
            executorService.execute(() -> callback.accept(finalData));
        });
    }

    /**
     * Deletes a file asynchronously.
     *
     * @param key   the name of the file to be deleted
     */
    public void deleteAsync(String key) {
        executorService.execute(() -> {
            String fullPath = basePath + key + ".json"; // Construct full path
            File file = new File(fullPath);
            if (file.exists()) {
                file.delete();
            }
        });
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
            String fullPath = basePath + "versions/" + versionedFileName; // Adjust for version saving
            File versionDir = new File(basePath + "versions/");
            if (!versionDir.exists()) {
                versionDir.mkdirs();
            }
            Files.write(Paths.get(fullPath), jsonData.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}