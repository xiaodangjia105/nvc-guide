package nvc.guide.modules.nvcassistant.trace;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Trace 上下文（ThreadLocal 持有）
 *
 * <p>在一次完整对话过程中，所有组件共享同一个 TraceContext，
 * 通过 ThreadLocal 传递，无需显式参数传递。
 */
@Data
public class TraceContext {

    private AgentTraceEntity trace;
    private int spanSequence;

    /** 临时存储 Span 列表，endTrace 时一次性落库 */
    private final List<AgentSpanEntity> spans = new ArrayList<>();

    public TraceContext(AgentTraceEntity trace) {
        this.trace = trace;
        this.spanSequence = 0;
    }

    /**
     * 获取下一个序列号
     */
    public int nextSequence() {
        return ++spanSequence;
    }
}
