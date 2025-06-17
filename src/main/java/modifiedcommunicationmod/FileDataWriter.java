package modifiedcommunicationmod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

public class FileDataWriter implements Runnable {

    private final BlockingQueue<String> queue;
    private final String outputFilePath;
    private final Path finalPath;
    private final Path parentDir;
    private static final Logger logger = LogManager.getLogger(FileDataWriter.class.getName());
    private boolean verbose;

    public FileDataWriter(BlockingQueue<String> queue, String outputFilePath, boolean verbose) {
        this.queue = queue;
        this.outputFilePath = outputFilePath;
        this.finalPath = Paths.get(outputFilePath);
        this.parentDir = finalPath.getParent();
        this.verbose = verbose;
    }

    public void run() {
        try {
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
        } catch (IOException e) {
            logger.error("Could not create parent directories: " + e.getMessage(), e);
            return;
        }
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String message = queue.take();
                if (verbose) {
                    logger.info("Preparing to write message: " + message);
                }
                // Write to a temporary file, then move it into place, so a reader never sees a partial message
                Path tempPath = parentDir.resolve(outputFilePath + ".tmp." + UUID.randomUUID());
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempPath.toFile()))) {
                    writer.write(message);
                    writer.flush();
                }
                if (verbose) {
                    logger.info("Message written to temporary file: " + tempPath);
                }
                Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                if (verbose) {
                    logger.info("Renamed temporary file to final file: " + outputFilePath);
                }
            } catch (IOException e) {
                logger.error("Error writing message or renaming file: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                logger.info("File writing thread interrupted.");
                Thread.currentThread().interrupt();
            }
        }
    }
}
