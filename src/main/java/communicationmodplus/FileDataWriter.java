package communicationmodplus;

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
    // Serialises publishing to the output file between this thread and the
    // crash marker, which is written from the render thread (GameCrashReporter).
    private static final Object PUBLISH_LOCK = new Object();
    // Once the crash marker is out, nothing may overwrite it: the agent reads
    // only the newest output, and the game publishes nothing true after a crash.
    private static volatile boolean crashed = false;
    private static volatile String activeOutputPath = null;

    public FileDataWriter(BlockingQueue<String> queue, String outputFilePath, boolean verbose) {
        activeOutputPath = outputFilePath;
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
                synchronized (PUBLISH_LOCK) {
                    if (crashed) {
                        continue;
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
                }
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

    /**
     * Publish the crash marker from the calling thread, and stop publishing anything after it.
     *
     * Synchronous rather than queued: the game is about to shut down, and the
     * writer thread is interrupted on the way out, so a queued marker could be
     * dropped. The lock means a state the writer thread is publishing either
     * lands before the marker or not at all.
     */
    public static void publishCrashMarker(String message) {
        String outputFilePath = activeOutputPath;
        if (outputFilePath == null) {
            return;
        }
        synchronized (PUBLISH_LOCK) {
            crashed = true;
            try {
                Path finalPath = Paths.get(outputFilePath, new String[0]);
                Path parentDir = finalPath.toAbsolutePath().getParent();
                Path tempPath = parentDir.resolve(finalPath.getFileName().toString() + ".crash." + UUID.randomUUID());
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempPath.toFile()));){
                    writer.write(message);
                    writer.flush();
                }
                Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e) {
                logger.error("Could not publish the crash marker: " + e.getMessage(), (Throwable)e);
            }
        }
    }
}
