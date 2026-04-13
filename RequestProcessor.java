import java.util.concurrent.*;

/**
 * RequestProcessor.java
 *
 * Runs on a dedicated thread. Dequeues CalcTask objects from the shared
 * LinkedBlockingQueue in FIFO order, evaluates each math expression using
 * MathEvaluator, and routes the result back to the originating client via
 * the callback stored in the task.
 *
 * This ensures all CALC requests are processed globally in arrival order
 * regardless of which client sent them (AC-6 of MS-01).
 *
 * @author Gaurang Dhanani & Vedant Deshmukh
 */
public class RequestProcessor implements Runnable {

    /** Shared queue fed by all ClientHandler threads */
    private final BlockingQueue<CalcTask> queue;

    /** Flag to allow clean shutdown */
    private volatile boolean running = true;

    public RequestProcessor(BlockingQueue<CalcTask> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        while (running) {
            try {
                CalcTask task = queue.take(); // blocks until a task arrives
                String result = MathEvaluator.processCalcRequest(task.parts);
                task.callback.accept(result);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running = false;
    }

    // ── Inner class: a queued calculation task ───────────────────────────

    /**
     * Bundles the parsed CALC message fields and a callback to send the
     * result back to the originating ClientHandler.
     */
    public static class CalcTask {
        /** Parsed CALC fields: ["CALC", op1, operator, op2] */
        public final String[] parts;

        /** Callback — called by RequestProcessor with the result string */
        public final java.util.function.Consumer<String> callback;

        public CalcTask(String[] parts, java.util.function.Consumer<String> callback) {
            this.parts    = parts;
            this.callback = callback;
        }
    }
}