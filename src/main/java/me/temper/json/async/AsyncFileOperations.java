package me.temper.json.async;

import com.google.gson.Gson;

import java.io.*;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * A class for performing asynchronous file operations.
 */
public class AsyncFileOperations {

    private final File dataFolder;
    private final Gson gson;
    private final ExecutorService executorService;

    /**
     * Creates a new instance of the AsyncFileOperations class.
     *
     * @param dataFolder the folder where data will be stored
     */
    public AsyncFileOperations(File dataFolder) {
        this.dataFolder = dataFolder;
        this.gson = new Gson();
        this.executorService = Executors.newCachedThreadPool();
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
                File file = new File(dataFolder, fileName + ".json");
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
                File file = new File(dataFolder, fileName + ".json");
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
            File file = new File(dataFolder, key + ".json");
            if (file.exists()) {
                file.delete();
            }
        });
    }
}