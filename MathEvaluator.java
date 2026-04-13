import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * MathEvaluator.java
 *
 * Evaluates basic math expressions received via the CALC protocol message.
 * Supports addition, subtraction, multiplication, and division on
 * double-precision operands.
 *
 * Usage:
 *   String response = MathEvaluator.evaluate("12", "+", "8");
 *   // returns "RESULT|12+8|20"
 *
 *   String error = MathEvaluator.evaluate("10", "/", "0");
 *   // returns "ERROR|Division by zero"
 *
 * This class is used by the RequestProcessor (MS-01) to compute results
 * for incoming CALC requests.
 *
 * @author Gaurang
 * @see Protocol
 */
public final class MathEvaluator {

    /**
     * Evaluate a math expression from parsed CALC message fields.
     *
     * Given two operand strings and an operator string, this method:
     *   1. Validates the operator is supported (+, -, *, /)
     *   2. Parses both operands as doubles
     *   3. Checks for division by zero (if applicable)
     *   4. Computes the result
     *   5. Formats and returns a full RESULT or ERROR protocol message
     *
     * @param op1Str   the first operand as a string (e.g., "12", "-3.5")
     * @param operator the math operator (one of +, -, *, /)
     * @param op2Str   the second operand as a string
     * @return a protocol-formatted response string (without trailing newline),
     *         either "RESULT|<expr>|<answer>" or "ERROR|<message>"
     */
    public static String evaluate(String op1Str, String operator, String op2Str) {

        // ── Step 1: Validate operator ────────────────────────────────────
        if (!Protocol.isSupportedOperator(operator)) {
            return Protocol.MSG_ERROR + Protocol.DELIMITER
                    + Protocol.ERR_UNSUPPORTED_OP + operator;
        }

        // ── Step 2: Parse operands ───────────────────────────────────────
        double operand1;
        double operand2;

        try {
            operand1 = Double.parseDouble(op1Str.trim());
        } catch (NumberFormatException e) {
            return Protocol.MSG_ERROR + Protocol.DELIMITER
                    + Protocol.ERR_INVALID_NUMBER;
        }

        try {
            operand2 = Double.parseDouble(op2Str.trim());
        } catch (NumberFormatException e) {
            return Protocol.MSG_ERROR + Protocol.DELIMITER
                    + Protocol.ERR_INVALID_NUMBER;
        }

        // ── Step 3: Check for special input values (Infinity, NaN) ───────
        if (Double.isInfinite(operand1) || Double.isNaN(operand1)
                || Double.isInfinite(operand2) || Double.isNaN(operand2)) {
            return Protocol.MSG_ERROR + Protocol.DELIMITER
                    + Protocol.ERR_INVALID_NUMBER;
        }

        // ── Step 4: Check division by zero ───────────────────────────────
        if (operator.equals("/") && operand2 == 0.0) {
            return Protocol.MSG_ERROR + Protocol.DELIMITER
                    + Protocol.ERR_DIVISION_BY_ZERO;
        }

        // ── Step 5: Compute result ───────────────────────────────────────
        double result;

        switch (operator) {
            case "+":
                result = operand1 + operand2;
                break;
            case "-":
                result = operand1 - operand2;
                break;
            case "*":
                result = operand1 * operand2;
                break;
            case "/":
                result = operand1 / operand2;
                break;
            default:
                // Should never reach here due to Step 1, but defensive coding
                return Protocol.MSG_ERROR + Protocol.DELIMITER
                        + Protocol.ERR_UNSUPPORTED_OP + operator;
        }

        // ── Step 6: Check for overflow / NaN in result ───────────────────
        if (Double.isInfinite(result) || Double.isNaN(result)) {
            return Protocol.MSG_ERROR + Protocol.DELIMITER
                    + Protocol.ERR_INVALID_NUMBER;
        }

        // ── Step 7: Format the result ────────────────────────────────────
        // Build expression string: operand1 + operator + operand2 (no spaces)
        String expression = op1Str.trim() + operator + op2Str.trim();

        // Format answer: up to 6 decimal places, trailing zeros stripped
        String answer = formatResult(result);

        return Protocol.MSG_RESULT + Protocol.DELIMITER
                + expression + Protocol.DELIMITER + answer;
    }

    /**
     * Validate and process a raw CALC message (all fields including the prefix).
     *
     * This is a convenience method that checks field count before delegating
     * to {@link #evaluate(String, String, String)}.
     *
     * @param parts the parsed message fields (e.g., ["CALC", "12", "+", "8"])
     * @return a protocol-formatted response string (without trailing newline)
     */
    public static String processCalcRequest(String[] parts) {
        // CALC messages must have exactly 4 fields: CALC | op1 | operator | op2
        if (parts.length != 4) {
            return Protocol.MSG_ERROR + Protocol.DELIMITER
                    + Protocol.ERR_INVALID_FORMAT;
        }

        return evaluate(parts[1], parts[2], parts[3]);
    }

    /**
     * Format a double result with up to 6 decimal places, stripping
     * trailing zeros. For example:
     *   20.0       → "20"
     *   25.125     → "25.125"
     *   7.333333   → "7.333333"
     *   0.100000   → "0.1"
     *
     * @param result the computed result value
     * @return the formatted string representation
     */
    static String formatResult(double result) {
        // Use BigDecimal to strip trailing zeros cleanly
        // Round to 6 decimal places first to avoid floating-point noise
        BigDecimal bd = BigDecimal.valueOf(result)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        // toPlainString avoids scientific notation (e.g., "1E+2" → "100")
        return bd.toPlainString();
    }

    // ── Main (quick self-test) ───────────────────────────────────────────

    /**
     * Runs a quick self-test demonstrating all evaluation paths.
     */
    public static void main(String[] args) {
        System.out.println("=== MathEvaluator Self-Test ===\n");

        // Normal operations
        String[][] testCases = {
            {"10", "+", "5",   "RESULT|10+5|15"},
            {"7.5", "*", "3",  "RESULT|7.5*3|22.5"},
            {"100", "/", "4",  "RESULT|100/4|25"},
            {"-3", "*", "7",   "RESULT|-3*7|-21"},
            {"1", "/", "3",    "RESULT|1/3|0.333333"},
            {"0.1", "+", "0.2","RESULT|0.1+0.2|0.3"},
        };

        // Error cases
        String[][] errorCases = {
            {"10", "/", "0",   "ERROR|Division by zero"},
            {"abc", "+", "5",  "ERROR|Invalid number format"},
            {"10", "+", "xyz", "ERROR|Invalid number format"},
            {"10", "%", "5",   "ERROR|Unsupported operator: %"},
        };

        System.out.println("--- Normal Operations ---");
        for (String[] tc : testCases) {
            String result = evaluate(tc[0], tc[1], tc[2]);
            boolean pass = result.equals(tc[3]);
            System.out.printf("  %s %s %s = %s  %s%n",
                    tc[0], tc[1], tc[2], result,
                    pass ? "[PASS]" : "[FAIL] expected: " + tc[3]);
        }

        System.out.println("\n--- Error Cases ---");
        for (String[] tc : errorCases) {
            String result = evaluate(tc[0], tc[1], tc[2]);
            boolean pass = result.equals(tc[3]);
            System.out.printf("  %s %s %s = %s  %s%n",
                    tc[0], tc[1], tc[2], result,
                    pass ? "[PASS]" : "[FAIL] expected: " + tc[3]);
        }

        // Test processCalcRequest with field count validation
        System.out.println("\n--- processCalcRequest (field count) ---");
        String[] valid = {"CALC", "12", "+", "8"};
        String[] tooFew = {"CALC", "12"};
        String[] tooMany = {"CALC", "12", "+", "8", "extra"};

        System.out.println("  4 fields: " + processCalcRequest(valid));
        System.out.println("  2 fields: " + processCalcRequest(tooFew));
        System.out.println("  5 fields: " + processCalcRequest(tooMany));
    }

    /** Prevent instantiation — utility class. */
    private MathEvaluator() {
        throw new UnsupportedOperationException("MathEvaluator is a utility class");
    }
}
