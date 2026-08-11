# Architecture Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all critical and high-priority architecture issues identified in the review — N+1 queries, oversized Controller/Service, frontend type safety, dead code, and test coverage.

**Architecture:** 4-phase approach: performance fixes first (low risk), then structural refactoring (medium risk), then cleanup (low risk), then test coverage (supports future changes). Each phase produces a working, testable deliverable on its own branch.

**Tech Stack:** Spring Boot 4.0, Java 21, Spring Data JPA, PostgreSQL + pgvector, Redis, React 18, TypeScript, Vitest, JUnit 5, Mockito

## Global Constraints

- All changes on feature branches, never directly on master
- No breaking API changes — all existing endpoints must continue to work
- Each task ends with `./gradlew test` passing (backend) or `pnpm test` passing (frontend)
- Controller must never directly reference Entity classes after Phase 2
- No `any` type in frontend API calls after Phase 3

---

## Phase 1: Performance Fixes (branch: `fix/performance-n-plus-one`)

### Task 1.1: Add JOIN FETCH to NvcPracticeSessionRepository

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/repository/NvcPracticeSessionRepository.java`

**Interfaces:**
- Produces: `findByUserIdWithMessages(Long userId)` returning `List<NvcPracticeSessionEntity>` with messages eagerly fetched

- [ ] **Step 1: Add JOIN FETCH query methods**

```java
package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NvcPracticeSessionRepository extends JpaRepository<NvcPracticeSessionEntity, Long> {

    @Query("SELECT s FROM NvcPracticeSessionEntity s LEFT JOIN FETCH s.messages WHERE s.userId = :userId ORDER BY s.createdAt DESC")
    List<NvcPracticeSessionEntity> findByUserIdWithMessages(@Param("userId") Long userId);

    @Query("SELECT s FROM NvcPracticeSessionEntity s LEFT JOIN FETCH s.messages WHERE s.userId = :userId AND s.currentPhase = :phase ORDER BY s.createdAt DESC")
    List<NvcPracticeSessionEntity> findByUserIdAndPhaseWithMessages(@Param("userId") Long userId, @Param("phase") NvcSessionPhase phase);

    // Keep existing methods for cases where messages are not needed
    List<NvcPracticeSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<NvcPracticeSessionEntity> findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
        Long userId, NvcSessionPhase phase);

    Page<NvcPracticeSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<NvcPracticeSessionEntity> findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
        Long userId, NvcSessionPhase phase, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndCurrentPhase(Long userId, NvcSessionPhase phase);
}
```

- [ ] **Step 2: Update NvcPracticeSessionService to use JOIN FETCH methods**

In `NvcPracticeSessionService.java`, update the `getUserSessions(Long userId, NvcSessionPhase phase)` method (line 164-173) to use the new JOIN FETCH methods:

```java
public List<NvcPracticeSessionEntity> getUserSessions(
    Long userId, NvcSessionPhase phase) {
  if (phase != null) {
    return sessionRepository.findByUserIdAndPhaseWithMessages(userId, phase);
  }
  return sessionRepository.findByUserIdWithMessages(userId);
}
```

- [ ] **Step 3: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/repository/NvcPracticeSessionRepository.java app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java
git commit -m "fix: add JOIN FETCH to session queries to eliminate N+1 problem"
```

---

### Task 1.2: Add HNSW Index Migration Script

**Files:**
- Create: `app/src/main/resources/db/migration/V2__add_hnsw_index_knowledge_vectors.sql`

**Interfaces:**
- Produces: HNSW index on `knowledge_base_vectors.embedding` column

- [ ] **Step 1: Create migration directory and script**

```bash
mkdir -p app/src/main/resources/db/migration
```

Create `app/src/main/resources/db/migration/V2__add_hnsw_index_knowledge_vectors.sql`:

```sql
-- Replace IVFFlat index with HNSW for better query performance
-- HNSW provides faster approximate nearest neighbor search
-- m=16: number of connections per layer, ef_construction=100: build-time quality

-- Drop old index if exists (name may vary, using IF EXISTS for safety)
DROP INDEX IF EXISTS idx_knowledge_vectors_embedding;

-- Create HNSW index with cosine distance
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding
  ON knowledge_base_vectors USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 100);
```

- [ ] **Step 2: Verify the SQL is syntactically correct**

Review the migration script for correct pgvector HNSW syntax.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/resources/db/migration/
git commit -m "perf: add HNSW index migration for vector search optimization"
```

---

### Task 1.3: Enable Query Performance Monitoring

**Files:**
- Modify: `app/src/main/resources/application.yml`

**Interfaces:**
- Produces: Hibernate SQL logging enabled, Micrometer/Prometheus endpoint exposed

- [ ] **Step 1: Update application.yml**

In `app/src/main/resources/application.yml`, make these changes:

1. Change `show-sql: false` to `show-sql: true` (line 50) — for development visibility
2. Add `use_sql_comments: true` under `hibernate.properties` (after line 54)
3. Update the `management.endpoints.web.exposure.include` (line 285) to include `metrics,prometheus`

```yaml
  jpa:
    defer-datasource-initialization: true
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        use_sql_comments: true
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

- [ ] **Step 2: Add Micrometer Prometheus dependency**

In `app/build.gradle`, add to dependencies:

```groovy
implementation 'io.micrometer:micrometer-registry-prometheus'
```

- [ ] **Step 3: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/resources/application.yml app/build.gradle
git commit -m "perf: enable SQL logging and add Prometheus metrics endpoint"
```

---

### Task 1.4: Phase 1 Verification

- [ ] **Step 1: Run full backend test suite**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 2: Create PR for review**

```bash
git push origin fix/performance-n-plus-one
```

---

## Phase 2: Code Structure Optimization (branch: `refactor/controller-service-decouple`)

### Task 2.1: Create NvcPracticeFacade

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeFacade.java`

**Interfaces:**
- Consumes: All services currently injected into NvcPracticeController
- Produces: `NvcPracticeFacade` with methods: `createSession()`, `getUserSessions()`, `getSession()`, `sendMessage()`, `sendMessageStream()`, `getMessages()`, `getLatestEvaluation()`, `getSummary()`, `completeSession()`, `getStepProgress()`, `advanceStep()`, `resetStep()`, `getRecommendations()`, `toSessionResponse()`

- [ ] **Step 1: Create NvcPracticeFacade.java**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.result.PageResult;
import nvc.guide.modules.nvcpractice.dto.*;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Facade for NVC Practice module.
 * Orchestrates multiple services, keeping the Controller thin.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeFacade {

  private final NvcPracticeSessionService sessionService;
  private final NvcPracticeDialogueService dialogueService;
  private final NvcStructuredPracticeService structuredPracticeService;
  private final NvcEvaluationService evaluationService;
  private final NvcSummaryService summaryService;
  private final NvcScenarioService scenarioService;
  private final NvcAgentOrchestrator agentOrchestrator;

  // ========== Session Management ==========

  public PracticeSessionResponse createSession(Long userId, CreatePracticeSessionRequest req) {
    NvcPracticeSessionEntity session = sessionService.createSession(userId, req);
    return toSessionResponse(session);
  }

  public Page<PracticeSessionResponse> getUserSessions(Long userId, NvcSessionPhase phase, Pageable pageable) {
    return sessionService.getUserSessions(userId, phase, pageable).map(this::toSessionResponse);
  }

  public PracticeSessionResponse getSession(Long sessionId) {
    NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
    return toSessionResponse(session);
  }

  public PracticeSessionResponse completeSession(Long sessionId) {
    NvcPracticeSessionService.CompleteResult result = sessionService.completeAndEvaluate(sessionId);
    return toSessionResponse(result.session(), result.evaluationFailed());
  }

  // ========== Dialogue ==========

  public DialogueResponse sendMessage(Long sessionId, String content) {
    return dialogueService.sendMessage(sessionId, content);
  }

  public Flux<org.springframework.http.codec.ServerSentEvent<String>> sendMessageStream(Long sessionId, String content) {
    return dialogueService.sendMessageStream(sessionId, content);
  }

  public Page<MessageResponse> getMessages(Long sessionId, Pageable pageable) {
    return dialogueService.getMessages(sessionId, pageable);
  }

  // ========== Evaluation & Summary ==========

  public NvcEvaluationEntity getLatestEvaluation(Long sessionId) {
    return evaluationService.getLatestRealtimeEvaluation(sessionId).orElse(null);
  }

  public NvcSummaryEntity getSummary(Long sessionId) {
    return summaryService.getSummary(sessionId);
  }

  // ========== Structured Practice ==========

  public StepProgressDTO getStepProgress(Long sessionId) {
    return structuredPracticeService.getStepProgress(sessionId);
  }

  public StepProgressDTO advanceStep(Long sessionId) {
    return structuredPracticeService.advanceStep(sessionId);
  }

  public StepProgressDTO resetStep(Long sessionId) {
    return structuredPracticeService.resetStep(sessionId);
  }

  // ========== Recommendations ==========

  public List<NvcScenarioEntity> getRecommendations(Long userId, int limit) {
    return agentOrchestrator.recommendScenarios(userId, limit);
  }

  // ========== Response Mapping ==========

  public PracticeSessionResponse toSessionResponse(NvcPracticeSessionEntity session) {
    return toSessionResponse(session, false);
  }

  public PracticeSessionResponse toSessionResponse(NvcPracticeSessionEntity session, boolean evaluationFailed) {
    Long scenarioId = session.getScenarioId();
    String scenarioTitle = null;
    String scenarioDescription = null;
    if (scenarioId != null) {
      NvcScenarioEntity scenario = scenarioService.findById(scenarioId);
      if (scenario != null) {
        scenarioTitle = scenario.getTitle();
        scenarioDescription = scenario.getDescription();
      }
    }

    return new PracticeSessionResponse(
        session.getId(),
        session.getUserId(),
        session.getPracticeMode(),
        session.getCurrentPhase(),
        session.getCurrentStep(),
        session.getAgentScene() != null ? session.getAgentScene().name() : null,
        session.getDifficulty(),
        scenarioId,
        scenarioTitle,
        scenarioDescription,
        session.getStartedAt(),
        session.getCompletedAt(),
        session.getCreatedAt(),
        evaluationFailed
    );
  }
}
```

- [ ] **Step 2: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass (Facade is additive, no existing code changed yet)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeFacade.java
git commit -m "refactor: add NvcPracticeFacade to orchestrate practice services"
```

---

### Task 2.2: Refactor NvcPracticeController to Use Facade

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java`

**Interfaces:**
- Consumes: `NvcPracticeFacade` (from Task 2.1)
- Produces: Same API endpoints, zero behavior change

- [ ] **Step 1: Rewrite NvcPracticeController to use Facade**

Replace the entire controller content:

```java
package nvc.guide.modules.nvcpractice.controller;

import nvc.guide.common.annotation.RateLimit;
import nvc.guide.common.result.PageResult;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcpractice.dto.*;
import nvc.guide.modules.nvcpractice.model.*;
import nvc.guide.modules.nvcpractice.service.NvcPracticeFacade;
import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/practice")
@Slf4j
@RequiredArgsConstructor
@Validated
public class NvcPracticeController {

  private final NvcPracticeFacade practiceFacade;

  /**
   * 创建练习会话
   */
  @RateLimit(count = 10)
  @PostMapping("/sessions")
  public Result<PracticeSessionResponse> createSession(
      @RequestParam Long userId,
      @Valid @RequestBody CreatePracticeSessionRequest req) {
    return Result.success(practiceFacade.createSession(userId, req));
  }

  /**
   * 获取用户的练习会话列表
   */
  @GetMapping("/sessions")
  public Result<PageResult<PracticeSessionResponse>> getUserSessions(
      @RequestParam Long userId,
      @RequestParam(required = false) NvcSessionPhase phase,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    var pageResult = practiceFacade.getUserSessions(userId, phase, PageRequest.of(page, size));
    return Result.success(PageResult.of(pageResult));
  }

  /**
   * 获取单个会话详情
   */
  @GetMapping("/sessions/{sessionId}")
  public Result<PracticeSessionResponse> getSession(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getSession(sessionId));
  }

  /**
   * 发送消息（非流式）
   */
  @RateLimit(count = 30)
  @PostMapping("/sessions/{sessionId}/messages")
  public Result<DialogueResponse> sendMessage(
      @PathVariable Long sessionId,
      @Valid @RequestBody SendMessageRequest request) {
    return Result.success(practiceFacade.sendMessage(sessionId, request.content()));
  }

  /**
   * 发送消息（流式 SSE）
   */
  @RateLimit(count = 30)
  @PostMapping(
      value = "/sessions/{sessionId}/messages/stream",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<ServerSentEvent<String>> sendMessageStream(
      @PathVariable Long sessionId,
      @Valid @RequestBody SendMessageRequest request) {
    return practiceFacade.sendMessageStream(sessionId, request.content())
        .onErrorResume(e -> {
          log.error("Practice stream error: sessionId={}", sessionId, e);
          return Flux.just(ServerSentEvent.<String>builder()
              .event("error")
              .data("对话出错: " + e.getMessage())
              .build());
        });
  }

  /**
   * 获取对话历史
   */
  @GetMapping("/sessions/{sessionId}/messages")
  public Result<PageResult<MessageResponse>> getMessages(
      @PathVariable Long sessionId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    var pageResult = practiceFacade.getMessages(sessionId, PageRequest.of(page, size));
    return Result.success(PageResult.of(pageResult));
  }

  /**
   * 获取最新实时评估结果
   */
  @GetMapping("/sessions/{sessionId}/evaluation")
  public Result<NvcEvaluationEntity> getLatestEvaluation(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getLatestEvaluation(sessionId));
  }

  /**
   * 获取 NVC 四要素摘要
   */
  @GetMapping("/sessions/{sessionId}/summary")
  public Result<NvcSummaryEntity> getSummary(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getSummary(sessionId));
  }

  /**
   * 结束会话（含最终评估）
   */
  @RateLimit(count = 5)
  @PostMapping("/sessions/{sessionId}/complete")
  public Result<PracticeSessionResponse> completeSession(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.completeSession(sessionId));
  }

  /**
   * 获取结构化练习的步骤进度
   */
  @GetMapping("/sessions/{sessionId}/step-progress")
  public Result<StepProgressDTO> getStepProgress(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.getStepProgress(sessionId));
  }

  /**
   * 手动推进到下一步
   */
  @RateLimit(count = 10)
  @PostMapping("/sessions/{sessionId}/advance-step")
  public Result<StepProgressDTO> advanceStep(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.advanceStep(sessionId));
  }

  /**
   * 重置步骤（重新开始）
   */
  @RateLimit(count = 5)
  @PostMapping("/sessions/{sessionId}/reset-step")
  public Result<StepProgressDTO> resetStep(@PathVariable Long sessionId) {
    return Result.success(practiceFacade.resetStep(sessionId));
  }

  /**
   * 获取推荐练习场景（基于用户薄弱维度）
   */
  @GetMapping("/recommendations")
  public Result<List<NvcScenarioEntity>> getRecommendations(
      @RequestParam Long userId,
      @RequestParam(defaultValue = "5") int limit) {
    return Result.success(practiceFacade.getRecommendations(userId, limit));
  }
}
```

- [ ] **Step 2: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/controller/NvcPracticeController.java
git commit -m "refactor: slim down NvcPracticeController to use NvcPracticeFacade"
```

---

### Task 2.3: Extract NvcPracticeSessionValidator from NvcPracticeSessionService

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionValidator.java`
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java`

**Interfaces:**
- Produces: `NvcPracticeSessionValidator.validatePhaseTransition(NvcSessionPhase current, NvcSessionPhase target, Long sessionId)`

- [ ] **Step 1: Create NvcPracticeSessionValidator.java**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Validates NVC practice session state transitions.
 * Extracted from NvcPracticeSessionService to follow Single Responsibility Principle.
 */
@Component
@Slf4j
public class NvcPracticeSessionValidator {

  /**
   * Valid state transitions table.
   * Key: current state → Value: allowed target states
   */
  private static final Map<NvcSessionPhase, Set<NvcSessionPhase>> VALID_TRANSITIONS = Map.of(
      NvcSessionPhase.CREATED, Set.of(NvcSessionPhase.IN_PROGRESS, NvcSessionPhase.COMPLETED),
      NvcSessionPhase.IN_PROGRESS, Set.of(NvcSessionPhase.PAUSED, NvcSessionPhase.COMPLETED),
      NvcSessionPhase.PAUSED, Set.of(NvcSessionPhase.IN_PROGRESS, NvcSessionPhase.COMPLETED),
      NvcSessionPhase.COMPLETED, Set.of(NvcSessionPhase.EVALUATED),
      NvcSessionPhase.EVALUATED, Set.of()  // Terminal state
  );

  /**
   * Validate that a phase transition is allowed.
   *
   * @throws BusinessException if the transition is not allowed
   */
  public void validatePhaseTransition(NvcSessionPhase currentPhase, NvcSessionPhase newPhase, Long sessionId) {
    Set<NvcSessionPhase> allowed = VALID_TRANSITIONS.getOrDefault(currentPhase, Set.of());
    if (!allowed.contains(newPhase)) {
      log.warn("Invalid phase transition attempted: sessionId={}, {} -> {}",
          sessionId, currentPhase, newPhase);
      throw new BusinessException(
          ErrorCode.INVALID_OPERATION,
          "不允许从 " + currentPhase + " 转换到 " + newPhase);
    }
  }
}
```

- [ ] **Step 2: Update NvcPracticeSessionService to use Validator**

In `NvcPracticeSessionService.java`:
1. Add `NvcPracticeSessionValidator` as a dependency (inject via constructor)
2. Remove the `VALID_TRANSITIONS` static field (lines 47-53)
3. Remove the `validateTransitions()` method (lines 92-101)
4. In `updatePhase()` (line 191), replace the inline validation with:

```java
public NvcPracticeSessionEntity updatePhase(
    Long sessionId, NvcSessionPhase newPhase) {
  NvcPracticeSessionEntity session = getSession(sessionId);
  NvcSessionPhase currentPhase = session.getCurrentPhase();

  validator.validatePhaseTransition(currentPhase, newPhase, sessionId);

  session.setCurrentPhase(newPhase);

  if (newPhase == NvcSessionPhase.IN_PROGRESS
      && session.getStartedAt() == null) {
    session.setStartedAt(LocalDateTime.now());
  }
  if (newPhase == NvcSessionPhase.COMPLETED) {
    session.setCompletedAt(LocalDateTime.now());
  }

  return sessionRepository.save(session);
}
```

- [ ] **Step 3: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionValidator.java app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java
git commit -m "refactor: extract session validation logic to NvcPracticeSessionValidator"
```

---

### Task 2.4: Extract NvcAbilityService from NvcProfileService

**Files:**
- Create: `app/src/main/java/nvc/guide/modules/nvcprofile/service/NvcAbilityService.java`
- Modify: `app/src/main/java/nvc/guide/modules/nvcprofile/service/NvcProfileService.java`

**Interfaces:**
- Produces: `NvcAbilityService.getAbilityRadar(Long userId)`, `NvcAbilityService.getAbilityTrends(Long userId)`, `NvcAbilityService.calculateLevel(Long userId)`

- [ ] **Step 1: Create NvcAbilityService.java**

```java
package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.dto.AbilityTrendDTO;
import nvc.guide.modules.nvcprofile.model.NvcLevel;
import nvc.guide.modules.nvcprofile.model.NvcUserAbilityScoreEntity;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles NVC ability scoring, radar chart, and trend calculations.
 * Extracted from NvcProfileService to follow Single Responsibility Principle.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAbilityService {

    private static final int MIN_SAMPLES_FOR_LEVEL = 3;
    private static final int RECENT_SCORES_WINDOW_SIZE = 10;
    private static final int ADVANCED_THRESHOLD = 80;
    private static final int INTERMEDIATE_THRESHOLD = 60;

    private final NvcUserAbilityScoreRepository abilityScoreRepository;

    /**
     * Get ability radar chart data (average of recent 10 practice sessions)
     */
    public AbilityRadarDTO getAbilityRadar(Long userId) {
        List<NvcUserAbilityScoreEntity> recentScores =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        if (recentScores.isEmpty()) {
            return new AbilityRadarDTO(0, 0, 0, 0, 0, 0, "BEGINNER");
        }

        List<NvcUserAbilityScoreEntity> last10 = recentScores.subList(
            0, Math.min(RECENT_SCORES_WINDOW_SIZE, recentScores.size()));

        int avgObservation = (int) last10.stream()
            .filter(s -> s.getObservation() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getObservation).average().orElse(0);
        int avgFeeling = (int) last10.stream()
            .filter(s -> s.getFeeling() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getFeeling).average().orElse(0);
        int avgNeed = (int) last10.stream()
            .filter(s -> s.getNeed() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getNeed).average().orElse(0);
        int avgRequest = (int) last10.stream()
            .filter(s -> s.getRequest() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getRequest).average().orElse(0);
        int avgEmpathy = (int) last10.stream()
            .filter(s -> s.getEmpathy() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getEmpathy)
            .average().orElse(0);
        int overallAvg = (int) Math.round((avgObservation + avgFeeling + avgNeed + avgRequest) / 4.0);

        return new AbilityRadarDTO(
            avgObservation, avgFeeling, avgNeed, avgRequest, avgEmpathy,
            overallAvg, calculateLevel(userId).name()
        );
    }

    /**
     * Get ability trend data (recent 30 sessions)
     */
    public List<AbilityTrendDTO> getAbilityTrends(Long userId) {
        List<NvcUserAbilityScoreEntity> scores =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        return scores.stream()
            .map(s -> new AbilityTrendDTO(
                s.getScoredAt(),
                s.getObservation(),
                s.getFeeling(),
                s.getNeed(),
                s.getRequest(),
                s.getEmpathy(),
                s.getPracticeType() != null ? s.getPracticeType().name() : null
            ))
            .toList();
    }

    /**
     * Calculate NVC level based on recent 10 practice sessions
     */
    public NvcLevel calculateLevel(Long userId) {
        List<NvcUserAbilityScoreEntity> recent =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        if (recent.size() < MIN_SAMPLES_FOR_LEVEL) {
            return NvcLevel.BEGINNER;
        }

        List<NvcUserAbilityScoreEntity> last10 = recent.subList(0, Math.min(RECENT_SCORES_WINDOW_SIZE, recent.size()));
        double avgOverall = last10.stream()
            .mapToInt(s -> {
                int obs = s.getObservation() != null ? s.getObservation() : 0;
                int feel = s.getFeeling() != null ? s.getFeeling() : 0;
                int need = s.getNeed() != null ? s.getNeed() : 0;
                int req = s.getRequest() != null ? s.getRequest() : 0;
                return (int) Math.round((obs + feel + need + req) / 4.0);
            })
            .average()
            .orElse(0);

        if (avgOverall >= ADVANCED_THRESHOLD) return NvcLevel.ADVANCED;
        if (avgOverall >= INTERMEDIATE_THRESHOLD) return NvcLevel.INTERMEDIATE;
        return NvcLevel.BEGINNER;
    }
}
```

- [ ] **Step 2: Update NvcProfileService to delegate to NvcAbilityService**

In `NvcProfileService.java`:
1. Add `NvcAbilityService` as a dependency
2. Remove `getAbilityRadar()`, `getAbilityTrends()`, `calculateLevel()` methods and related constants
3. Delegate calls to `NvcAbilityService`
4. Update `toDTO()` to use `abilityService.getAbilityRadar()`
5. Update `updateAbilityScore()` to use `abilityService.calculateLevel()`

- [ ] **Step 3: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcprofile/service/NvcAbilityService.java app/src/main/java/nvc/guide/modules/nvcprofile/service/NvcProfileService.java
git commit -m "refactor: extract ability scoring to NvcAbilityService"
```

---

### Task 2.5: Fix Module Coupling — Remove Cross-Module Repository Injection

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java`

**Interfaces:**
- Consumes: `NvcScenarioService.findById()` and `NvcScenarioService.findByDifficulty()`
- Removes: Direct `NvcScenarioRepository` injection

- [ ] **Step 1: Update NvcPracticeSessionService**

In `NvcPracticeSessionService.java`:
1. Remove the `NvcScenarioRepository scenarioRepository` field and constructor parameter
2. In `pickRandomScenario()` method (line 356), replace `scenarioRepository.findByDifficulty(d)` with `scenarioService.findByDifficulty(d)` and `scenarioRepository.findAll()` with `scenarioService.findAll()`

Note: Check if `NvcScenarioService` already has `findByDifficulty()` and `findAll()` methods. If not, add them.

- [ ] **Step 2: Verify NvcScenarioService has required methods**

Check `NvcScenarioService.java` for `findByDifficulty()` and `findAll()` methods. If missing, add:

```java
public List<NvcScenarioEntity> findByDifficulty(NvcDifficulty difficulty) {
    return scenarioRepository.findByDifficulty(difficulty);
}

public List<NvcScenarioEntity> findAll() {
    return scenarioRepository.findAll();
}
```

- [ ] **Step 3: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java
git commit -m "refactor: remove cross-module NvcScenarioRepository injection from NvcPracticeSessionService"
```

---

### Task 2.6: Phase 2 Verification

- [ ] **Step 1: Run full backend test suite**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 2: Verify Controller dependencies**

Check that `NvcPracticeController` has only 1 dependency (`NvcPracticeFacade`).

- [ ] **Step 3: Create PR for review**

```bash
git push origin refactor/controller-service-decouple
```

---

## Phase 3: Type Safety & Cleanup (branch: `fix/type-safety-and-cleanup`)

### Task 3.1: Fix Frontend `any` Types in API Calls

**Files:**
- Modify: `frontend/src/api/nvc.ts`
- Verify: `frontend/src/types/nvc.ts` (already has `NvcEvaluation` interface)

**Interfaces:**
- Consumes: `NvcEvaluation` from `types/nvc.ts` (already defined)
- Produces: Type-safe `getEvaluation()` and `getSummary()` calls

- [ ] **Step 1: Update nvc.ts to use proper types**

In `frontend/src/api/nvc.ts`, change lines 55-63:

```typescript
  getEvaluation: (sessionId: number) =>
    request.get<NvcEvaluation>(
      `/api/nvc/practice/sessions/${sessionId}/evaluation`
    ),

  getSummary: (sessionId: number) =>
    request.get<NvcSummary>(
      `/api/nvc/practice/sessions/${sessionId}/summary`
    ),
```

Also add the `NvcSummary` type import at the top (line 3), and add `NvcEvaluation` to the import list.

- [ ] **Step 2: Add NvcSummary type if not exists**

Check `frontend/src/types/nvc.ts` for `NvcSummary` interface. If missing, add:

```typescript
export interface NvcSummary {
  id: number;
  sessionId: number;
  observationSummary: string;
  feelingSummary: string;
  needSummary: string;
  requestSummary: string;
  overallSummary: string;
  createdAt: string;
}
```

- [ ] **Step 3: Run frontend type check**

Run: `cd frontend && pnpm tsc --noEmit`
Expected: No type errors

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/nvc.ts frontend/src/types/nvc.ts
git commit -m "fix: replace any types with proper TypeScript interfaces in API calls"
```

---

### Task 3.2: Remove Hardcoded Backend URLs

**Files:**
- Modify: `frontend/src/api/request.ts` (line 12)
- Modify: `frontend/src/api/nvc.ts` (lines 38, 95)
- Modify: `frontend/src/api/knowledgebase.ts` (line 3)
- Create: `frontend/.env.development`
- Create: `frontend/.env.production`
- Create: `frontend/.env.example`

**Interfaces:**
- Produces: All backend URLs configured via `VITE_API_BASE_URL` environment variable

- [ ] **Step 1: Create environment files**

Create `frontend/.env.development`:
```
VITE_API_BASE_URL=http://localhost:8080
```

Create `frontend/.env.production`:
```
VITE_API_BASE_URL=
```

Create `frontend/.env.example`:
```
# Backend API base URL (empty for production when served from same origin)
VITE_API_BASE_URL=http://localhost:8080
```

- [ ] **Step 2: Update request.ts**

In `frontend/src/api/request.ts`, change line 12:

```typescript
const baseURL = import.meta.env.VITE_API_BASE_URL || '';
```

- [ ] **Step 3: Update nvc.ts**

In `frontend/src/api/nvc.ts`, change line 38:

```typescript
  sendMessageStream: (sessionId: number, content: string) =>
    fetch(
      (import.meta.env.VITE_API_BASE_URL || '')
      + `/api/nvc/practice/sessions/${sessionId}/messages/stream`,
```

And line 95:

```typescript
  downloadPdfUrl: (sessionId: number) =>
    (import.meta.env.VITE_API_BASE_URL || '')
    + `/api/nvc/report/sessions/${sessionId}/pdf`,
```

- [ ] **Step 4: Update knowledgebase.ts**

In `frontend/src/api/knowledgebase.ts`, change line 3:

```typescript
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
```

- [ ] **Step 5: Run frontend type check**

Run: `cd frontend && pnpm tsc --noEmit`
Expected: No type errors

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/request.ts frontend/src/api/nvc.ts frontend/src/api/knowledgebase.ts frontend/.env.development frontend/.env.production frontend/.env.example
git commit -m "fix: replace hardcoded backend URLs with VITE_API_BASE_URL env variable"
```

---

### Task 3.3: Clean Up Dead Code

**Files:**
- Modify: `app/src/main/java/nvc/guide/modules/nvcprofile/service/NvcProfileService.java` (remove `getProfileLegacy()` if exists)
- Modify: `app/src/main/java/nvc/guide/modules/knowledgebase/service/KnowledgeBaseParseService.java` (remove `parseOldFormat()` if exists)

- [ ] **Step 1: Search for dead code**

Run: `grep -r "getProfileLegacy\|parseOldFormat\|@Deprecated" app/src/main/java/ --include="*.java"`

Identify any `@Deprecated` methods or unused methods.

- [ ] **Step 2: Remove identified dead code**

Remove any confirmed unused methods. If methods are used elsewhere, skip them.

- [ ] **Step 3: Run backend tests**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: remove dead code and deprecated methods"
```

---

### Task 3.4: Phase 3 Verification

- [ ] **Step 1: Run frontend type check**

Run: `cd frontend && pnpm tsc --noEmit`
Expected: No type errors, zero `any` in API calls

- [ ] **Step 2: Verify no hardcoded URLs**

Run: `grep -r "localhost:8080" frontend/src/ --include="*.ts"`
Expected: No results (except possibly in comments or test mocks)

- [ ] **Step 3: Create PR for review**

```bash
git push origin fix/type-safety-and-cleanup
```

---

## Phase 4: Test Coverage (branch: `test/coverage-improvement`)

### Task 4.1: Add Unit Tests for NvcPracticeSessionValidator

**Files:**
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionValidatorTest.java`

**Interfaces:**
- Tests: `NvcPracticeSessionValidator.validatePhaseTransition()` for all valid and invalid transitions

- [ ] **Step 1: Create test class**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class NvcPracticeSessionValidatorTest {

    private NvcPracticeSessionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new NvcPracticeSessionValidator();
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED, IN_PROGRESS",
        "CREATED, COMPLETED",
        "IN_PROGRESS, PAUSED",
        "IN_PROGRESS, COMPLETED",
        "PAUSED, IN_PROGRESS",
        "PAUSED, COMPLETED",
        "COMPLETED, EVALUATED"
    })
    void validTransitions_shouldNotThrow(NvcSessionPhase from, NvcSessionPhase to) {
        assertDoesNotThrow(() -> validator.validatePhaseTransition(from, to, 1L));
    }

    @ParameterizedTest
    @CsvSource({
        "CREATED, PAUSED",
        "CREATED, EVALUATED",
        "IN_PROGRESS, CREATED",
        "PAUSED, CREATED",
        "COMPLETED, IN_PROGRESS",
        "EVALUATED, COMPLETED"
    })
    void invalidTransitions_shouldThrow(NvcSessionPhase from, NvcSessionPhase to) {
        assertThrows(BusinessException.class,
            () -> validator.validatePhaseTransition(from, to, 1L));
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd app && ../gradlew test --tests "nvc.guide.modules.nvcpractice.service.NvcPracticeSessionValidatorTest"`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionValidatorTest.java
git commit -m "test: add unit tests for NvcPracticeSessionValidator"
```

---

### Task 4.2: Add Unit Tests for NvcAbilityService

**Files:**
- Create: `app/src/test/java/nvc/guide/modules/nvcprofile/service/NvcAbilityServiceTest.java`

**Interfaces:**
- Tests: `NvcAbilityService.getAbilityRadar()`, `getAbilityTrends()`, `calculateLevel()`

- [ ] **Step 1: Create test class with Mockito**

```java
package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.dto.AbilityTrendDTO;
import nvc.guide.modules.nvcprofile.model.NvcLevel;
import nvc.guide.modules.nvcprofile.model.NvcUserAbilityScoreEntity;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NvcAbilityServiceTest {

    @Mock
    private NvcUserAbilityScoreRepository abilityScoreRepository;

    @InjectMocks
    private NvcAbilityService abilityService;

    @Test
    void getAbilityRadar_noScores_returnsZeros() {
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(Collections.emptyList());

        AbilityRadarDTO result = abilityService.getAbilityRadar(1L);

        assertEquals(0, result.observation());
        assertEquals("BEGINNER", result.level());
    }

    @Test
    void calculateLevel_insufficientSamples_returnsBeginner() {
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(List.of(
                createScore(80, 80, 80, 80, 80),
                createScore(80, 80, 80, 80, 80)
            ));

        NvcLevel level = abilityService.calculateLevel(1L);

        assertEquals(NvcLevel.BEGINNER, level);
    }

    @Test
    void calculateLevel_highScores_returnsAdvanced() {
        List<NvcUserAbilityScoreEntity> scores = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            scores.add(createScore(85, 85, 85, 85, 85));
        }
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(scores);

        NvcLevel level = abilityService.calculateLevel(1L);

        assertEquals(NvcLevel.ADVANCED, level);
    }

    private NvcUserAbilityScoreEntity createScore(int obs, int feel, int need, int req, int empathy) {
        return NvcUserAbilityScoreEntity.builder()
            .observation(obs)
            .feeling(feel)
            .need(need)
            .request(req)
            .empathy(empathy)
            .scoredAt(LocalDateTime.now())
            .build();
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd app && ../gradlew test --tests "nvc.guide.modules.nvcprofile.service.NvcAbilityServiceTest"`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/nvc/guide/modules/nvcprofile/service/NvcAbilityServiceTest.java
git commit -m "test: add unit tests for NvcAbilityService"
```

---

### Task 4.3: Add Unit Tests for NvcPracticeFacade

**Files:**
- Create: `app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcPracticeFacadeTest.java`

**Interfaces:**
- Tests: `NvcPracticeFacade` methods delegate correctly to underlying services

- [ ] **Step 1: Create test class**

```java
package nvc.guide.modules.nvcpractice.service;

import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NvcPracticeFacadeTest {

    @Mock private NvcPracticeSessionService sessionService;
    @Mock private NvcPracticeDialogueService dialogueService;
    @Mock private NvcStructuredPracticeService structuredPracticeService;
    @Mock private NvcEvaluationService evaluationService;
    @Mock private NvcSummaryService summaryService;
    @Mock private NvcScenarioService scenarioService;
    @Mock private NvcAgentOrchestrator agentOrchestrator;

    @InjectMocks
    private NvcPracticeFacade facade;

    @Test
    void createSession_delegatesToSessionService() {
        NvcPracticeSessionEntity mockSession = NvcPracticeSessionEntity.builder()
            .id(1L)
            .userId(100L)
            .practiceMode(NvcPracticeMode.FREE_DIALOG)
            .currentPhase(NvcSessionPhase.CREATED)
            .build();
        when(sessionService.createSession(eq(100L), any())).thenReturn(mockSession);

        var result = facade.createSession(100L, new CreatePracticeSessionRequest(
            NvcPracticeMode.FREE_DIALOG, null, null));

        assertNotNull(result);
        assertEquals(1L, result.id());
        verify(sessionService).createSession(eq(100L), any());
    }

    @Test
    void getLatestEvaluation_delegatesToEvaluationService() {
        when(evaluationService.getLatestRealtimeEvaluation(1L))
            .thenReturn(java.util.Optional.empty());

        var result = facade.getLatestEvaluation(1L);

        assertNull(result);
        verify(evaluationService).getLatestRealtimeEvaluation(1L);
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd app && ../gradlew test --tests "nvc.guide.modules.nvcpractice.service.NvcPracticeFacadeTest"`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/nvc/guide/modules/nvcpractice/service/NvcPracticeFacadeTest.java
git commit -m "test: add unit tests for NvcPracticeFacade"
```

---

### Task 4.4: Phase 4 Verification

- [ ] **Step 1: Run full backend test suite**

Run: `cd app && ../gradlew test`
Expected: All tests pass

- [ ] **Step 2: Check test coverage**

Run: `cd app && ../gradlew jacocoTestReport`
Review the coverage report for the refactored classes.

- [ ] **Step 3: Create PR for review**

```bash
git push origin test/coverage-improvement
```

---

## Summary

| Phase | Branch | Tasks | Est. Time |
|-------|--------|-------|-----------|
| Phase 1: Performance | `fix/performance-n-plus-one` | 4 tasks | 1 week |
| Phase 2: Structure | `refactor/controller-service-decouple` | 6 tasks | 2 weeks |
| Phase 3: Cleanup | `fix/type-safety-and-cleanup` | 4 tasks | 1 week |
| Phase 4: Tests | `test/coverage-improvement` | 4 tasks | 1-2 weeks |

**Total: 18 tasks across 4 phases**
