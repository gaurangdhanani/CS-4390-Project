import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/**
 * ClientHandler.java
 *
 * Handles a single connected client on its own thread.
 * Performs the JOIN/ACK handshake, reads CALC and QUIT messages,
 * enqueues CALC tasks to the shared RequestProcessor queue, and
 * manages clean/forced disconnection.
 *
 * @author Gaurang Dhanani & Vedant Deshmukh
 */
public class ClientHandler implements Runnable {

    private final Socket                      socket;
    private final BlockingQueue<RequestProcessor.CalcTask> queue;
    private final ServerLogger                logger;
    private final ConcurrentHashMap<String, ClientHandler> clients;

    private String       clientName = "UNKNOWN";
    private PrintWriter  out;

    public ClientHandler(Socket socket,
                         BlockingQueue<RequestProcessor.CalcTask> queue,
                         ServerLogger logger,
                         ConcurrentHashMap<String, ClientHandler> clients) {
        this.socket  = socket;
        this.queue   = queue;
        this.logger  = logger;
        this.clients = clients;
    }

    @Override
    public void run() {
        String ip   = socket.getInetAddress().getHostAddress();
        int    port = socket.getPort();

        try (
            BufferedReader in  = new BufferedReader(
                                     new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(
                                     new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            this.out = out;

            // ── Handshake: expect JOIN|<n> ───────────────────────────────
            String joinLine = in.readLine();
            if (joinLine == null) return;

            String[] joinParts = Protocol.parseMessage(joinLine);
            if (joinParts.length < 2 || !joinParts[0].equals(Protocol.MSG_JOIN)) {
                send(Protocol.buildMessage(Protocol.MSG_ERROR, Protocol.ERR_INVALID_FORMAT));
                return;
            }

            String name = joinParts[1].trim();
            if (name.isEmpty()) {
                send(Protocol.buildMessage(Protocol.MSG_ERROR, Protocol.ERR_NAME_EMPTY));
                return;
            }

            // Check duplicate name
            if (clients.containsKey(name)) {
                send(Protocol.buildMessage(Protocol.MSG_ERROR,
                        Protocol.ERR_NAME_IN_USE + name));
                return;
            }

            clientName = name;
            clients.put(clientName, this);
            logger.logConnect(clientName, ip, port);

            // Send ACK
            send(Protocol.buildMessage(Protocol.MSG_ACK, "Welcome " + clientName));

            // ── Request loop ─────────────────────────────────────────────
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = Protocol.parseMessage(line);
                if (parts.length == 0) continue;

                switch (parts[0]) {
                    case "CALC":
                        // Enqueue task; result routed back via callback
                        final String[] taskParts = parts;
                        queue.put(new RequestProcessor.CalcTask(taskParts, result -> {
                            // Log the request
                            if (taskParts.length == 4) {
                                String expr = taskParts[1] + " " + taskParts[2]
                                            + " " + taskParts[3];
                                String[] resParts = Protocol.parseMessage(result);
                                String ans = resParts.length >= 3 ? resParts[2] : result;
                                logger.logRequest(clientName, expr, ans);
                            }
                            send(Protocol.buildMessage(
                                result.split("\\|")[0].equals("ERROR")
                                    ? new String[]{result}
                                    : Protocol.parseMessage(result)));
                        }));
                        break;

                    case "QUIT":
                        logger.logDisconnect(clientName);
                        // Compute duration for BYE — ServerLogger already removes from map
                        send(Protocol.buildMessage(Protocol.MSG_BYE,
                                "Session ended. Goodbye " + clientName + "!"));
                        return;

                    default:
                        send(Protocol.buildMessage(Protocol.MSG_ERROR,
                                Protocol.ERR_INVALID_FORMAT));
                }
            }

            // Client disconnected without QUIT
            logger.logForcedDisconnect(clientName);

        } catch (IOException | InterruptedException e) {
            logger.logForcedDisconnect(clientName);
        } finally {
            clients.remove(clientName);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /** Thread-safe send to this client's output stream */
    public synchronized void send(String message) {
        if (out != null) {
            out.print(message);
            out.flush();
        }
    }

    /** Convenience overload for pre-split parts */
    private void send(String[] parts) {
        send(String.join(Protocol.DELIMITER, parts) + Protocol.TERMINATOR);
    }
}