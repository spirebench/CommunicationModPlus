package communicationmodplus;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

public class FileDataReader implements Runnable {

    private final BlockingQueue<String> queue;
    private final String inputFilePath;
    private static final Logger logger = LogManager.getLogger(FileDataReader.class.getName());
    private boolean verbose;

    public FileDataReader(BlockingQueue<String> queue, String inputFilePath, boolean verbose) {
        this.queue = queue;
        this.inputFilePath = inputFilePath;
        this.verbose = verbose;
    }

    public void run() {
        File inputFile = new File(inputFilePath);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = "";
                if (inputFile.exists() && inputFile.length() > 0) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
                        String line;
                        StringBuilder content = new StringBuilder();
                        while ((line = reader.readLine()) != null) {
                            content.append(line).append("\n");
                        }
                        message = content.toString().trim();
                    } catch (IOException e) {
                        logger.error("Error reading input file: " + e.getMessage());
                        Thread.sleep(100);
                        continue;
                    }
                }
                if (!message.isEmpty()) {
                    if (verbose) {
                        logger.info("Read message from file: " + message);
                    }
                    queue.put(message);
                    // Clear the file once its message is queued
                    try (FileWriter writer = new FileWriter(inputFile, false)) {
                    } catch (IOException e) {
                        logger.error("Could not clear input file: " + e.getMessage());
                    }
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.info("File reading thread interrupted.");
                Thread.currentThread().interrupt();
            }
        }
    }
}
