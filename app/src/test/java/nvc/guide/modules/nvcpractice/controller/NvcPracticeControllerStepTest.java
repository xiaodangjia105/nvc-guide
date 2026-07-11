package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.service.NvcPracticeDialogueService;
import nvc.guide.modules.nvcpractice.service.NvcPracticeSessionService;
import nvc.guide.modules.nvcpractice.service.NvcStructuredPracticeService;
import nvc.guide.modules.nvcpractice.listener.NvcEvaluateStreamProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcPracticeController - Step Progress API 测试")
class NvcPracticeControllerStepTest {

  @Mock private NvcPracticeSessionService sessionService;
  @Mock private NvcPracticeDialogueService dialogueService;
  @Mock private NvcEvaluateStreamProducer evaluateStreamProducer;
  @Mock private NvcStructuredPracticeService structuredPracticeService;

  @InjectMocks private NvcPracticeController controller;

  @Test
  @DisplayName("GET step-progress 返回当前步骤进度")
  void getStepProgress_returnsProgress() {
    StepProgressDTO progress = new StepProgressDTO(
        NvcPracticeStep.OBSERVE, 0, 4, null, false,
        "步骤1/4：观察练习 — 学会区分观察（客观事实）和评论（主观判断）",
        10, null, null, 30
    );
    when(structuredPracticeService.getStepProgress(1L)).thenReturn(progress);

    var result = controller.getStepProgress(1L);

    assertEquals(200, result.getCode());
    assertNotNull(result.getData());
    assertEquals(NvcPracticeStep.OBSERVE, result.getData().currentStep());
    assertEquals(0, result.getData().stepIndex());
    assertEquals(4, result.getData().totalSteps());
  }

  @Test
  @DisplayName("POST advance-step 推进到下一步并返回新进度")
  void advanceStep_returnsNewProgress() {
    StepProgressDTO progress = new StepProgressDTO(
        NvcPracticeStep.FEELING, 1, 4, null, false,
        "步骤2/4：感受练习 — 学会识别和表达真实感受，区分感受和想法",
        10, null, null, 30
    );
    when(structuredPracticeService.advanceStep(1L)).thenReturn(progress);

    var result = controller.advanceStep(1L);

    assertEquals(200, result.getCode());
    assertNotNull(result.getData());
    assertEquals(NvcPracticeStep.FEELING, result.getData().currentStep());
    assertEquals(1, result.getData().stepIndex());
  }

  @Test
  @DisplayName("POST reset-step 重置步骤并返回初始进度")
  void resetStep_returnsResetProgress() {
    StepProgressDTO progress = new StepProgressDTO(
        NvcPracticeStep.OBSERVE, 0, 4, null, false,
        "步骤1/4：观察练习 — 学会区分观察（客观事实）和评论（主观判断）",
        10, null, null, 30
    );
    when(structuredPracticeService.resetStep(1L)).thenReturn(progress);

    var result = controller.resetStep(1L);

    assertEquals(200, result.getCode());
    assertNotNull(result.getData());
    assertEquals(NvcPracticeStep.OBSERVE, result.getData().currentStep());
    assertEquals(0, result.getData().stepIndex());
  }

  @Test
  @DisplayName("GET step-progress 返回 COMPLETED 状态")
  void getStepProgress_completedStep_returnsCompletedStatus() {
    StepProgressDTO progress = new StepProgressDTO(
        NvcPracticeStep.COMPLETED, 4, 4, null, false,
        "四步练习全部完成！进入综合练习。",
        null, null, null, null
    );
    when(structuredPracticeService.getStepProgress(1L)).thenReturn(progress);

    var result = controller.getStepProgress(1L);

    assertEquals(200, result.getCode());
    assertEquals(NvcPracticeStep.COMPLETED, result.getData().currentStep());
    assertEquals(4, result.getData().stepIndex());
    assertEquals(4, result.getData().totalSteps());
  }
}
