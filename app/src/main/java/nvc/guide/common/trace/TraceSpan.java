package nvc.guide.common.trace;

/**
 * Interface for a trace span, abstracting away the JPA entity details.
 * Defined in common layer to break the reverse dependency on modules/nvcassistant.
 */
public interface TraceSpan {

    void setInputPayload(String inputPayload);

    void setOutputPayload(String outputPayload);

    void setDurationMs(Long durationMs);

    String getSpanType();

    Long getDurationMs();
}
