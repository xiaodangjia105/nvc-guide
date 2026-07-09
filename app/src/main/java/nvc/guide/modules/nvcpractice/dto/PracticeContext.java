package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 练习上下文 — 传递给 Agent 的所有信息
 */
@Data
@Builder
public class PracticeContext {

  private NvcPracticeSessionEntity session;
  private List<NvcPracticeMessageEntity> recentMessages;
  private NvcEvaluationEntity lastEvaluation;
  private int roundCount;
  /** 用户档案摘要（注入到系统提示词），Step 5 接入 */
  private String userProfileSummary;
  /** 场景描述（场景驱动模式） */
  private String scenarioDescription;
  /** RAG 检索到的知识（注入到系统提示词），后续知识库模块接入 */
  private String ragContext;
  private NvcScenarioEntity scenario;
}
