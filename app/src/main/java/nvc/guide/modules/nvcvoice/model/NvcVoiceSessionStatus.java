package nvc.guide.modules.nvcvoice.model;

/**
 * NVC 语音练习会话状态
 */
public enum NvcVoiceSessionStatus {

  /** 进行中 - WebSocket 已连接，练习进行中 */
  IN_PROGRESS,

  /** 已暂停 - 用户暂停或超时 */
  PAUSED,

  /** 已完成 - 练习结束 */
  COMPLETED,

  /** 失败 - 发生错误 */
  FAILED
}
