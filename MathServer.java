import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * MathServer.java
 *
 * CE/CS 4390 — Computer Networks — Spring 2026
 * Math Server — main entry point.
 *
 * Starts a TCP ServerSocket, accepts client connections into a thread pool,
 * and routes all CALC requests through a shared FIFO queue processed by
 * a dedicated RequestProcessor thread.
 *
 * Usage:
 *   java MathServer [port]        (default port: 6789)
 *
 * @author Gaurang Dhanani & Vedant Deshmukh
 */
public class MathServer {

    public static void main(String[] args) throws IOException {

        int port = Protocol.DEFAULT_PORT;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("[ERROR] Invalid port: " + args[0]);
                System.exit(1);
            }
        }

        // Shared state
        BlockingQueue<RequestProcessor.CalcTask>      queue   = new LinkedBlockingQueue<>();
        ConcurrentHashMap<String, ClientHandler>       clients = new ConcurrentHashMap<>();
        ServerLogger                                   logger;

        try {
            logger = new ServerLogger();
        } catch (IOException e) {
            System.err.println("[ERROR] Cannot open server.log: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Start RequestProcessor on its own thread
        RequestProcessor processor = new RequestProcessor(queue);
        Thread processorThread = new Thread(processor, "RequestProcessor");
        processorThread.setDaemon(true);
        processorThread.start();

        // Thread pool for client handlers
        ExecutorService pool = Executors.newCachedThreadPool();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[SERVER] Shutting down...");
            processor.stop();
            pool.shutdownNow();
            logger.close();
        }));

        System.out.println("[SERVER] Math Server started on port " + port);
        System.out.println("[SERVER] Waiting for clients... (Ctrl+C to stop)\n");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(new ClientHandler(clientSocket, queue, logger, clients));
            }
        }
    }
}