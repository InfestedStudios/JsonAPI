package me.temper.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    private Gson gson;

    @Setter
    @Getter
    private StorageMode storageMode = StorageMode.CACHE_THEN_DISK;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private static String basePath = "./"; // Default base path

    @Getter
    @Setter
    public boolean debugMode = false;

    public JsonAPI(String basePath) {
        this.basePath = basePath.endsWith(File.separator) ? basePath : basePath + File.separator; // Ensure basePath ends with separator
        this.executorService = Executors.newCachedThreadPool();

        // Create a custom Gson instance
        this.gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation() // Exclude fields without @Expose annotation
                .create();
    }

    public static JsonAPI getInstance() {
        return new JsonAPI(basePath);
    }

    public <T> void store(String fileName, T data, StorageMode storageMode) {
        if (debugMode) {
            System.out.println("Storing data to file: " + fileName + " with storage mode: " + storageMode);
        }

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

    public <T> void store(String key, T data, Predicate<T> validator, Function<T, T> transformBeforeStore, StorageMode storageMode) {
        if (debugMode) {
            System.out.println("Storing data to file: " + key + " with storage mode: " + storageMode);
        }

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

    public <T> void load(String fileName, Class<T> typeOfT, Consumer<T> callback, StorageMode storageMode) {
        if (debugMode) {
            System.out.println("Loading data from file: " + fileName + " with storage mode: " + storageMode);
        }
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

    public <T> T load(String key, Type typeOfT, Function<T, T> transformAfterLoad, StorageMode storageMode) {
        if (debugMode) {
            System.out.println("Loading data from file: " + key + " with storage mode: " + storageMode);
        }
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

    public void clearData(String fileName) {
        cache.remove(fileName);
        File file = new File(fileName + ".json");
        if (file.exists()) {
            file.delete();
            if (debugMode) {
                System.out.println("Cleared data from file: " + fileName + " + Cleared Cache:" + cache);
            }
        }
    }

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

    public <T> void saveAsync(String fileName, T data) {
        if (debugMode) {
            System.out.println("Saving Async data from file: " + fileName + " with storage mode: " + storageMode);
        }
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

    public <T> void loadAsync(String fileName, Type typeOfT, Consumer<T> callback) {
        if (debugMode) {
            System.out.println("Loading Async data from file: " + fileName + " with storage mode: " + storageMode);
        }
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

    public void deleteAsync(String key) {
        if (debugMode) {
            System.out.println("Cleared Async data from file: " + key + " with storage mode: " + storageMode);
        }

        executorService.execute(() -> {
            String fullPath = basePath + key + ".json"; // Construct full path
            File file = new File(fullPath);
            if (file.exists()) {
                file.delete();
            }
        });
    }

    public static <T> void saveVersion(String fileName, T jsonData) {
        try {
            String versionTimestamp = dateFormat.format(new Date());
            String versionedFileName = fileName + "_" + versionTimestamp + ".json";
            String fullPath = basePath + "versions/" + versionedFileName; // Adjust for version saving
            File versionDir = new File(basePath + "versions/");
            if (!versionDir.exists()) {
                versionDir.mkdirs();
            }
            Files.write(Paths.get(fullPath), (byte[]) jsonData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteVersion(String fileName) {
        try {
            String versionTimestamp = dateFormat.format(new Date());
            String versionedFileName = fileName + "_" + versionTimestamp + ".json";
            String fullPath = basePath + "versions/" + versionedFileName; // Adjust for version saving
            File versionDir = new File(basePath + "versions/");
            if (versionDir.exists()) {
                for (File file : versionDir.listFiles()) {
                    if (file.getName().startsWith(fileName)) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void logDebug(String message) {
        if (debugMode) {
            System.out.println(message);
        }
    }
}
