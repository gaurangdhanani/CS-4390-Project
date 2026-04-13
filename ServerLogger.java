import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ServerLogger.java
 *
 * Thread-safe logging system for the Math Server.
 * Records client connections, math requests, errors, and disconnections
 * with timestamps to both the console (System.out) and a persistent
 * log file (server.log).
 *
 * Log format:
 *   [yyyy-MM-dd HH:mm:ss] EVENT_TYPE | ClientName | Details
 *
 * Usage:
 *   ServerLogger logger = new ServerLogger();
 *   logger.logConnect("Alice", "192.168.1.5", 52340);
 *   logger.logRequest("Alice", "12 + 8", "20");
 *   logger.logError("Alice", "Division by zero");
 *   logger.logDisconnect("Alice");
 *
 * @author Gaurang
 * @see Protocol
 */
public class ServerLogger {

    /** Timestamp format for log entries */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Log file writer (append mode) */
    private final PrintWriter fileWriter;

    /** Tracks when each client connected, keyed by client name */
    private final ConcurrentHashMap<String, LocalDateTime> connectTimes;

    /**
     * Create a new ServerLogger that writes to both console and server.log.
     * The log file is opened in append mode so it is not overwritten on restart.
     *
     * @throws IOException if the log file cannot be opened
     */
    public ServerLogger() throws IOException {
        // Open server.log in append mode (true = append)
        this.fileWriter = new PrintWriter(
                new BufferedWriter(new FileWriter("server.log", true)), true);
        this.connectTimes = new ConcurrentHashMap<>();
    }

    // ── Public logging methods ───────────────────────────────────────────

    /**
     * Log a client connection event.
     *
     * @param clientName the display name of the client
     * @param ip         the client's IP address
     * @param port       the client's port number
     */
    public void logConnect(String clientName, String ip, int port) {
        connectTimes.put(clientName, LocalDateTime.now());
        writeLog("CONNECT", clientName, ip + ":" + port);
    }

    /**
     * Log a math request and its result.
     *
     * @param clientName the client who sent the request
     * @param expression the math expression (e.g., "12 + 8")
     * @param result     the computed result (e.g., "20")
     */
    public void logRequest(String clientName, String expression, String result) {
        writeLog("REQUEST", clientName, expression + " = " + result);
    }

    /**
     * Log an error event.
     *
     * @param clientName the client that caused the error
     * @param message    the error message
     */
    public void logError(String clientName, String message) {
        writeLog("ERROR", clientName, message);
    }

    /**
     * Log a client disconnection event with session duration.
     * Duration is computed from the stored connect time.
     *
     * @param clientName the client that disconnected
     */
    public void logDisconnect(String clientName) {
        String duration = computeDuration(clientName);
        writeLog("DISCONNECT", clientName, "Duration: " + duration);
    }

    /**
     * Log a forced disconnection (client crashed without sending QUIT).
     *
     * @param clientName the client that disconnected unexpectedly
     */
    public void logForcedDisconnect(String clientName) {
        String duration = computeDuration(clientName);
        writeLog("DISCONNECT", clientName, "Forced disconnect. Duration: " + duration);
    }

    // ── Duration helper ──────────────────────────────────────────────────

    /**
     * Compute session duration and remove the client from the connect times map.
     * Format: "Xm Ys" (e.g., "5m 32s", "0m 8s").
     *
     * @param clientName the client name to look up
     * @return formatted duration string
     */
    private String computeDuration(String clientName) {
        LocalDateTime connectTime = connectTimes.remove(clientName);

        if (connectTime == null) {
            return "0m 0s";
        }

        Duration duration = Duration.between(connectTime, LocalDateTime.now());
        long totalSeconds = duration.getSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return minutes + "m " + seconds + "s";
    }

    // ── Core write method (synchronized for thread safety) ───────────────

    /**
     * Write a log entry to both console and file.
     * Synchronized to prevent interleaved output from concurrent threads.
     *
     * @param eventType  the event type (CONNECT, REQUEST, ERROR, DISCONNECT)
     * @param clientName the client name
     * @param details    event-specific details
     */
    private synchronized void writeLog(String eventType, String clientName, String details) {
        String timestamp = LocalDateTime.now().format(FORMATTER);

        // Pad event type for alignment
        String entry = String.format("[%s] %-10s | %-10s | %s",
                timestamp, eventType, clientName, details);

        // Write to console
        System.out.println(entry);

        // Write to file
        fileWriter.println(entry);
        fileWriter.flush();
    }

    /**
     * Close the log file writer. Call this when the server shuts down.
     */
    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }

    // ── Main (quick demo) ────────────────────────────────────────────────

    /**
     * Runs a quick demo simulating client activity and log output.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=== ServerLogger Demo ===\n");

        ServerLogger logger = new ServerLogger();

        // Simulate Alice connecting and doing some math
        logger.logConnect("Alice", "192.168.1.5", 52340);
        Thread.sleep(1000);  // simulate 1 second of activity

        logger.logRequest("Alice", "12 + 8", "20");
        logger.logRequest("Alice", "100 / 4", "25");

        // Simulate Bob connecting
        logger.logConnect("Bob", "192.168.1.7", 52455);
        logger.logRequest("Bob", "7 * 6", "42");

        // Simulate an error
        logger.logError("Bob", "Division by zero");

        // Simulate disconnections
        Thread.sleep(1000);
        logger.logDisconnect("Alice");
        logger.logDisconnect("Bob");

        logger.close();

        System.out.println("\n--- Contents of server.log ---");
        BufferedReader reader = new BufferedReader(new FileReader("server.log"));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        reader.close();
    }
}
