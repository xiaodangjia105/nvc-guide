package nvc.guide.modules.nvcassistant.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcassistant.trace.dto.TraceStats;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TraceStatsService {

    private final AgentTraceRepository traceRepository;
    private final AgentSpanRepository spanRepository;

    /**
     * 获取 Trace 统计概览
     */
    public TraceStats getStats(LocalDateTime from, LocalDateTime to) {
        List<AgentTraceEntity> traces = traceRepository.findByCreatedAtBetween(from, to);

        if (traces.isEmpty()) {
            return TraceStats.builder()
                .totalTraces(0).avgDurationMs(0).avgTokensPerTrace(0)
                .successRate(0).statusCounts(Map.of()).modeCounts(Map.of())
                .topFailureReasons(List.of())
                .build();
        }

        long totalDuration = 0, totalTokens = 0, successCount = 0;
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        Map<String, Long> modeCounts = new LinkedHashMap<>();
        Map<String, Long> failureReasons = new LinkedHashMap<>();

        for (AgentTraceEntity t : traces) {
            totalDuration += t.getTotalDurationMs();
            totalTokens += t.getTotalInputTokens() + t.getTotalOutputTokens();
            if ("SUCCESS".equals(t.getFinalStatus())) successCount++;

            statusCounts.merge(t.getFinalStatus(), 1L, Long::sum);
            modeCounts.merge(t.getMode(), 1L, Long::sum);

            // 收集失败原因（从 Span 中提取）
            if (!"SUCCESS".equals(t.getFinalStatus())) {
                List<AgentSpanEntity> spans = spanRepository.findByTraceIdOrderBySequenceAsc(t.getTraceId());
                for (AgentSpanEntity span : spans) {
                    if ("FAILED".equals(span.getStatus()) && span.getFailureReason() != null) {
                        failureReasons.merge(span.getFailureReason(), 1L, Long::sum);
                    }
                }
            }
        }

        int size = traces.size();
        List<TraceStats.FailureReason> topReasons = failureReasons.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(e -> TraceStats.FailureReason.builder().reason(e.getKey()).count(e.getValue()).build())
            .toList();

        return TraceStats.builder()
            .totalTraces(size)
            .avgDurationMs(Math.round((double) totalDuration / size * 100) / 100.0)
            .avgTokensPerTrace(Math.round((double) totalTokens / size * 100) / 100.0)
            .successRate(Math.round((double) successCount / size * 10000) / 100.0)
            .statusCounts(statusCounts)
            .modeCounts(modeCounts)
            .topFailureReasons(topReasons)
            .build();
    }
}
