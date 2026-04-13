/**
 * Protocol.java
 * 
 * Shared constants for the Math Server communication protocol (v1.0).
 * This class defines message type prefixes, delimiters, and defaults
 * used by both the server and client applications.
 * 
 * Protocol overview:
 *   - All messages are newline-terminated UTF-8 strings.
 *   - Fields within a message are separated by the pipe character '|'.
 *   - Seven message types: JOIN, ACK, CALC, RESULT, ERROR, QUIT, BYE.
 * 
 * See protocol_spec.md for the full specification.
 * 
 * @author Gaurang
 */
public final class Protocol {

    // ── Message type prefixes ────────────────────────────────────────────

    /** Client → Server: register with a display name. Format: JOIN|<name> */
    public static final String MSG_JOIN   = "JOIN";

    /** Server → Client: acknowledge successful registration. Format: ACK|<message> */
    public static final String MSG_ACK    = "ACK";

    /** Client → Server: request a math calculation. Format: CALC|<op1>|<operator>|<op2> */
    public static final String MSG_CALC   = "CALC";

    /** Server → Client: return a calculation result. Format: RESULT|<expr>|<answer> */
    public static final String MSG_RESULT = "RESULT";

    /** Server → Client: report an error. Format: ERROR|<message> */
    public static final String MSG_ERROR  = "ERROR";

    /** Client → Server: request disconnection. Format: QUIT */
    public static final String MSG_QUIT   = "QUIT";

    /** Server → Client: confirm disconnection. Format: BYE|<message> */
    public static final String MSG_BYE    = "BYE";

    // ── Formatting constants ─────────────────────────────────────────────

    /** Field delimiter used between message parts */
    public static final String DELIMITER  = "|";

    /** Message terminator (newline) */
    public static final String TERMINATOR = "\n";

    // ── Limits & defaults ────────────────────────────────────────────────

    /** Maximum allowed message length in bytes (including terminator) */
    public static final int MAX_MESSAGE_LENGTH = 1024;

    /** Default server port if none is specified on the command line */
    public static final int DEFAULT_PORT = 6789;

    /** Default server IP used by the client when none is specified */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** Socket timeout in milliseconds for the client handshake phase */
    public static final int HANDSHAKE_TIMEOUT_MS = 10_000;

    // ── Supported operators ──────────────────────────────────────────────

    /** Set of valid math operators */
    public static final String[] SUPPORTED_OPERATORS = { "+", "-", "*", "/" };

    // ── Error message templates ──────────────────────────────────────────

    public static final String ERR_INVALID_FORMAT      = "Invalid request format";
    public static final String ERR_DIVISION_BY_ZERO    = "Division by zero";
    public static final String ERR_INVALID_NUMBER      = "Invalid number format";
    public static final String ERR_UNSUPPORTED_OP      = "Unsupported operator: ";
    public static final String ERR_NAME_IN_USE         = "Name already in use: ";
    public static final String ERR_NAME_EMPTY          = "Client name cannot be empty";

    // ── Protocol version ─────────────────────────────────────────────────

    public static final String VERSION = "1.0";

    // ── Helper methods ───────────────────────────────────────────────────

    /**
     * Build a protocol message from the given parts.
     * Example: buildMessage("CALC", "12", "+", "8") → "CALC|12|+|8\n"
     *
     * @param parts the message type followed by field values
     * @return the formatted message string (with trailing newline)
     */
    public static String buildMessage(String... parts) {
        return String.join(DELIMITER, parts) + TERMINATOR;
    }

    /**
     * Parse a raw protocol message into its component fields.
     * The trailing newline (if present) is stripped before splitting.
     *
     * @param raw the raw message string received from the socket
     * @return an array of field values
     */
    public static String[] parseMessage(String raw) {
        if (raw == null) {
            return new String[0];
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new String[0];
        }
        return trimmed.split("\\" + DELIMITER, -1);
    }

    /**
     * Check whether the given operator is one of the supported math operators.
     *
     * @param op the operator string to check
     * @return true if the operator is supported
     */
    public static boolean isSupportedOperator(String op) {
        for (String supported : SUPPORTED_OPERATORS) {
            if (supported.equals(op)) {
                return true;
            }
        }
        return false;
    }

    // ── Main (protocol quick-reference) ─────────────────────────────────

    /**
     * Prints a quick-reference summary of the protocol and runs
     * a few sample buildMessage / parseMessage demonstrations.
     */
    public static void main(String[] args) {
        System.out.println("=== Math Server Protocol ===");
        System.out.println("Delimiter : '" + DELIMITER + "'");
        System.out.println("Terminator: '\\n'");
        System.out.println("Default port: " + DEFAULT_PORT);
        System.out.println("Default host: " + DEFAULT_HOST);
        System.out.println();

        System.out.println("Message Types:");
        System.out.println("  JOIN   - Client → Server  : JOIN|<name>");
        System.out.println("  ACK    - Server → Client  : ACK|<message>");
        System.out.println("  CALC   - Client → Server  : CALC|<op1>|<operator>|<op2>");
        System.out.println("  RESULT - Server → Client  : RESULT|<expr>|<answer>");
        System.out.println("  ERROR  - Server → Client  : ERROR|<message>");
        System.out.println("  QUIT   - Client → Server  : QUIT");
        System.out.println("  BYE    - Server → Client  : BYE|<message>");
        System.out.println();

        System.out.println("Supported operators: +, -, *, /");
        System.out.println();

        // Demonstrate buildMessage
        System.out.println("--- buildMessage examples ---");
        String joinMsg = buildMessage(MSG_JOIN, "Alice");
        String calcMsg = buildMessage(MSG_CALC, "12", "+", "8");
        String quitMsg = buildMessage(MSG_QUIT);
        System.out.println("  JOIN  : " + joinMsg.trim());
        System.out.println("  CALC  : " + calcMsg.trim());
        System.out.println("  QUIT  : " + quitMsg.trim());
        System.out.println();

        // Demonstrate parseMessage
        System.out.println("--- parseMessage examples ---");
        String[] parsed = parseMessage("CALC|12|+|8");
        System.out.print("  'CALC|12|+|8' → [");
        for (int i = 0; i < parsed.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print("\"" + parsed[i] + "\"");
        }
        System.out.println("]");

        // Demonstrate isSupportedOperator
        System.out.println();
        System.out.println("--- isSupportedOperator examples ---");
        System.out.println("  '+' → " + isSupportedOperator("+"));
        System.out.println("  '%' → " + isSupportedOperator("%"));
    }

    /** Prevent instantiation — this is a constants-only utility class. */
    private Protocol() {
        throw new UnsupportedOperationException("Protocol is a utility class");
    }
}