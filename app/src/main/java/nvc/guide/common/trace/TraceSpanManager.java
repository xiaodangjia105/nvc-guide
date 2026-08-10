package nvc.guide.common.trace;

/**
 * Interface for managing trace spans, abstracting away the TraceManager implementation.
 * Defined in common layer to break the reverse dependency on modules/nvcassistant.
 */
public interface TraceSpanManager {

    /**
     * Start a new span.
     *
     * @param spanType      the span type (e.g., HTTP_REQUEST, LLM_CALL)
     * @param componentName the component name
     * @return a new TraceSpan instance
     */
    TraceSpan startSpan(String spanType, String componentName);

    /**
     * End a span with the given status.
     *
     * @param span          the span to end
     * @param status        the status (SUCCESS / DEGRADED / FAILED)
     * @param failureReason the failure reason, or null
     */
    void endSpan(TraceSpan span, String status, String failureReason);
}
