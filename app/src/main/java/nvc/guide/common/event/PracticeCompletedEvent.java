package nvc.guide.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 练习完成事件
 * 当练习会话完成并评估后发布，各模块独立监听
 */
@Getter
public class PracticeCompletedEvent extends ApplicationEvent {

    private final Long sessionId;
    private final Long userId;
    private final boolean evaluationFailed;

    public PracticeCompletedEvent(Object source, Long sessionId, Long userId,
                                   boolean evaluationFailed) {
        super(source);
        this.sessionId = sessionId;
        this.userId = userId;
        this.evaluationFailed = evaluationFailed;
    }
}
