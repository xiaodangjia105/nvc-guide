package nvc.guide.common;

/**
 * 练习上下文标识 — 将频繁出现的 (sessionId, userId, scene) 数据团提取为 record。
 *
 * <p>此 record 仅承载标识信息，不包含业务实体。
 * 用于替代方法签名中分散的 sessionId / userId / scene 参数，
 * 减少 Data Clumps 问题。
 *
 * @param sessionId 会话 ID
 * @param userId    用户 ID
 * @param scene     场景标识（通常是 {@code NvcAgentScene.name()}，也可为 null）
 */
public record PracticeContext(Long sessionId, Long userId, String scene) {

  /**
   * 创建不含 scene 的上下文（scene 稍后确定时使用）
   */
  public PracticeContext(Long sessionId, Long userId) {
    this(sessionId, userId, null);
  }
}
