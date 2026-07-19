package nvc.guide.modules.knowledgebase.model;

/**
 * 知识库类型枚举
 * 用于区分不同用途的知识库，支持按类型过滤检索
 */
public enum KnowledgeBaseType {

  /** NVC 理论知识（四要素、核心原则等） */
  NVC_THEORY,

  /** 话术模板（职场/家庭/亲密关系场景话术） */
  SPEECH_TEMPLATE,

  /** 情绪词汇（正面/负面感受词汇、需求词汇） */
  EMOTION_VOCAB,

  /** 用户案例（成功/失败案例对比） */
  USER_CASE,

  /** 个人 Wiki（按用户隔离） */
  PERSONAL_WIKI
}
