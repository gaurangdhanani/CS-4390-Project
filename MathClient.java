import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * MathClient.java
 *
 * CE/CS 4390 — Computer Networks — Spring 2026
 * Math Server Client Application
 *
 * Implements MS-04 (Connection & Handshake) and MS-05 (Request Loop & Disconnect).
 *
 * Usage:
 *   java MathClient [serverIP] [serverPort]
 *   java MathClient                          (uses defaults: 127.0.0.1 : 6789)
 *   java MathClient 192.168.1.10 6789
 *
 * Session flow:
 *   1. Prompt user for display name.
 *   2. Connect to server via TCP.
 *   3. Send JOIN|<name> and wait for ACK (MS-04).
 *   4. Enter interactive math loop — send CALC requests, display results (MS-05).
 *   5. On "quit" or "exit", send QUIT and display BYE before terminating.
 *
 * @author Vedant Deshmukh
 * @see Protocol
 */
public class MathClient {

    // ── MS-04: Connection & Handshake ────────────────────────────────────

    /**
     * Entry point. Parses optional IP/port args, prompts for name,
     * establishes TCP connection, performs handshake, then enters request loop.
     */
    public static void main(String[] args) {

        // ── Parse command-line args (IP and port are optional) ───────────
        String serverIP   = Protocol.DEFAULT_HOST;
        int    serverPort = Protocol.DEFAULT_PORT;

        if (args.length >= 1) {
            serverIP = args[0];
        }
        if (args.length >= 2) {
            try {
                serverPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("[ERROR] Invalid port number: " + args[1]);
                System.exit(1);
            }
        }

        // ── Prompt for client display name ───────────────────────────────
        Scanner stdin = new Scanner(System.in);
        String clientName = "";

        while (clientName.isEmpty()) {
            System.out.print("Enter your display name: ");
            clientName = stdin.nextLine().trim();
            if (clientName.isEmpty()) {
                System.out.println("[ERROR] Name cannot be empty. Please try again.");
            }
        }

        // ── Establish TCP connection ─────────────────────────────────────
        System.out.println("[INFO] Connecting to " + serverIP + ":" + serverPort + " ...");

        Socket socket;
        try {
            socket = new Socket();
            // Set connection timeout to 10 seconds (AC-8)
            socket.connect(new InetSocketAddress(serverIP, serverPort),
                           Protocol.HANDSHAKE_TIMEOUT_MS);
        } catch (ConnectException e) {
            System.err.println("[ERROR] Connection refused. Is the server running at "
                    + serverIP + ":" + serverPort + "?");
            System.exit(1);
            return;
        } catch (UnknownHostException e) {
            System.err.println("[ERROR] Unknown host: " + serverIP);
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("[ERROR] Could not connect: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("[INFO] Connected.");

        // ── Run handshake + request loop ─────────────────────────────────
        try (
            BufferedReader  in  = new BufferedReader(
                                      new InputStreamReader(socket.getInputStream()));
            PrintWriter     out = new PrintWriter(
                                      new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            // ── MS-04: Handshake ─────────────────────────────────────────
            if (!performHandshake(clientName, in, out, socket)) {
                return; // handshake failed — error already printed
            }

            // Reset socket timeout for the interactive request loop (AC-8)
            socket.setSoTimeout(0);

            // ── MS-05: Interactive request loop ──────────────────────────
            runRequestLoop(clientName, stdin, in, out);

        } catch (IOException e) {
            System.err.println("[ERROR] I/O error: " + e.getMessage());
            System.exit(1);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            stdin.close();
        }
    }

    // ── MS-04: Handshake ─────────────────────────────────────────────────

    /**
     * Send JOIN message and wait for ACK from server.
     *
     * @param name   display name chosen by the user
     * @param in     server input stream
     * @param out    server output stream
     * @param socket the connected socket (used to set timeout)
     * @return true if handshake succeeded, false otherwise
     */
    private static boolean performHandshake(String name,
                                             BufferedReader in,
                                             PrintWriter    out,
                                             Socket         socket) throws IOException {

        // Set socket timeout for handshake phase (AC-8)
        socket.setSoTimeout(Protocol.HANDSHAKE_TIMEOUT_MS);

        // Send JOIN|<name> (AC-2)
        String joinMsg = Protocol.buildMessage(Protocol.MSG_JOIN, name);
        out.print(joinMsg);
        out.flush();
        System.out.println("[INFO] Sent: " + joinMsg.trim());

        // Wait for server response (AC-3)
        String response;
        try {
            response = in.readLine();
        } catch (SocketTimeoutException e) {
            System.err.println("[ERROR] Server did not respond within "
                    + (Protocol.HANDSHAKE_TIMEOUT_MS / 1000) + " seconds. Exiting.");
            System.exit(1);
            return false;
        }

        if (response == null) {
            System.err.println("[ERROR] Server closed connection unexpectedly.");
            System.exit(1);
            return false;
        }

        String[] parts = Protocol.parseMessage(response);

        // ACK → proceed (AC-4)
        if (parts.length >= 2 && parts[0].equals(Protocol.MSG_ACK)) {
            System.out.println("[SERVER] " + parts[1]);  // e.g. "Welcome Alice"
            return true;
        }

        // ERROR → display message and exit (AC-6)
        if (parts.length >= 2 && parts[0].equals(Protocol.MSG_ERROR)) {
            System.err.println("[ERROR] Server rejected JOIN: " + parts[1]);
            System.exit(1);
            return false;
        }

        // Unexpected response
        System.err.println("[ERROR] Unexpected server response: " + response);
        System.exit(1);
        return false;
    }

    // ── MS-05: Request loop & disconnect ─────────────────────────────────

    /**
     * Interactive math loop.
     *
     * Reads expressions from stdin in the form:  <operand1> <operator> <operand2>
     * Formats as CALC|op1|operator|op2 and sends to server.
     * Displays RESULT or ERROR responses.
     * "quit" or "exit" triggers clean disconnection.
     *
     * @param name   client display name (for display only)
     * @param stdin  scanner over System.in
     * @param in     server input stream
     * @param out    server output stream
     */
    private static void runRequestLoop(String name,
                                        Scanner        stdin,
                                        BufferedReader in,
                                        PrintWriter    out) {

        printHelp();

        while (true) {
            // ── Show prompt (AC-1) ───────────────────────────────────────
            System.out.print("math> ");
            System.out.flush();

            String userInput;
            try {
                userInput = stdin.nextLine();
            } catch (Exception e) {
                // stdin closed (e.g. Ctrl+D / piped input exhausted)
                sendQuit(in, out);
                break;
            }

            if (userInput == null) {
                sendQuit(in, out);
                break;
            }

            userInput = userInput.trim();

            if (userInput.isEmpty()) {
                continue;
            }

            // ── Quit commands (AC-5) ─────────────────────────────────────
            if (userInput.equalsIgnoreCase("quit")
                    || userInput.equalsIgnoreCase("exit")) {
                sendQuit(in, out);
                break;
            }

            // ── Help command ─────────────────────────────────────────────
            if (userInput.equalsIgnoreCase("help")) {
                printHelp();
                continue;
            }

            // ── Parse user input (AC-2, AC-9) ────────────────────────────
            String[] parts = userInput.split("\\s+");

            // Expect exactly 3 parts: operand1 operator operand2
            if (parts.length != 3) {
                System.out.println("[CLIENT] Invalid format. Use:  <number> <+|-|*|/> <number>");
                System.out.println("         Example:  12 + 8    or    100.5 / 4");
                continue;
            }

            String op1      = parts[0];
            String operator = parts[1];
            String op2      = parts[2];

            // Client-side validation: check operator (AC-9)
            if (!Protocol.isSupportedOperator(operator)) {
                System.out.println("[CLIENT] Unsupported operator '" + operator
                        + "'. Supported: + - * /");
                continue;
            }

            // Client-side validation: check operands are numeric (AC-9)
            if (!isNumeric(op1) || !isNumeric(op2)) {
                System.out.println("[CLIENT] Operands must be numbers. Got: '"
                        + op1 + "' and '" + op2 + "'");
                continue;
            }

            // ── Build and send CALC message (AC-3) ───────────────────────
            String calcMsg = Protocol.buildMessage(Protocol.MSG_CALC, op1, operator, op2);
            out.print(calcMsg);
            out.flush();

            // ── Read and display server response (AC-4) ──────────────────
            String response;
            try {
                response = in.readLine();
            } catch (IOException e) {
                System.err.println("[ERROR] Lost connection to server: " + e.getMessage());
                break;
            }

            if (response == null) {
                // Server disconnected mid-session (AC-7)
                System.err.println("[ERROR] Server disconnected unexpectedly.");
                break;
            }

            displayResponse(response);
        }
    }

    // ── Quit helper ──────────────────────────────────────────────────────

    /**
     * Send QUIT to server and wait for BYE response (AC-5, AC-6).
     */
    private static void sendQuit(BufferedReader in, PrintWriter out) {
        System.out.println("[INFO] Disconnecting...");

        // Send QUIT
        out.print(Protocol.buildMessage(Protocol.MSG_QUIT));
        out.flush();

        // Wait for BYE (AC-6)
        try {
            String bye = in.readLine();
            if (bye != null) {
                String[] parts = Protocol.parseMessage(bye);
                if (parts.length >= 2 && parts[0].equals(Protocol.MSG_BYE)) {
                    System.out.println("[SERVER] " + parts[1]);
                } else {
                    System.out.println("[SERVER] " + bye);
                }
            }
        } catch (IOException e) {
            // Server may have already closed — that's fine
        }

        System.out.println("[INFO] Connection closed. Goodbye!");
    }

    // ── Display helpers ──────────────────────────────────────────────────

    /**
     * Format and print a server response (RESULT or ERROR) in a user-friendly way (AC-4).
     */
    private static void displayResponse(String response) {
        String[] parts = Protocol.parseMessage(response);

        if (parts.length == 0) {
            System.out.println("[SERVER] (empty response)");
            return;
        }

        switch (parts[0]) {
            case "RESULT":
                // RESULT|<expr>|<answer>
                if (parts.length >= 3) {
                    System.out.println("  = " + parts[2]
                            + "   (" + parts[1] + ")");
                } else {
                    System.out.println("[RESULT] " + response);
                }
                break;

            case "ERROR":
                // ERROR|<message>
                if (parts.length >= 2) {
                    System.out.println("[ERROR] " + parts[1]);
                } else {
                    System.out.println("[ERROR] " + response);
                }
                break;

            default:
                System.out.println("[SERVER] " + response);
        }
    }

    /**
     * Print a short usage guide.
     */
    private static void printHelp() {
        System.out.println();
        System.out.println("  Math Server Client — ready!");
        System.out.println("  Enter expressions like:  12 + 8   |   100.5 / 4   |   -3 * 7");
        System.out.println("  Operators supported   :  +   -   *   /");
        System.out.println("  Type  quit  or  exit  to disconnect.");
        System.out.println();
    }

    // ── Utility ──────────────────────────────────────────────────────────

    /**
     * Check whether a string can be parsed as a double.
     *
     * @param s the string to check
     * @return true if it's a valid number
     */
    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
