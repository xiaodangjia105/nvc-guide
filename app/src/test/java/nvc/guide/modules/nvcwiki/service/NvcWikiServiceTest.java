package nvc.guide.modules.nvcwiki.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.knowledgebase.model.VectorStatus;
import nvc.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import nvc.guide.modules.knowledgebase.service.KnowledgeBaseVectorService;
import nvc.guide.modules.nvcpractice.dto.RagResult;
import nvc.guide.modules.nvcpractice.service.NvcRagService;
import nvc.guide.modules.nvcwiki.dto.WikiCreateRequest;
import nvc.guide.modules.nvcwiki.dto.WikiResponse;
import nvc.guide.modules.nvcwiki.dto.WikiUpdateRequest;
import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.model.NvcWikiSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcWikiService 测试")
class NvcWikiServiceTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private KnowledgeBaseVectorService vectorService;
    @Mock
    private NvcRagService ragService;

    private NvcWikiService service;

    @BeforeEach
    void setUp() {
        service = new NvcWikiService(knowledgeBaseRepository, vectorService, ragService);
    }

    private KnowledgeBaseEntity buildEntity(Long id, Long userId, String name) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setType(KnowledgeBaseType.PERSONAL_WIKI);
        entity.setName(name);
        entity.setCategory(NvcWikiCategory.OTHER.name());
        entity.setContentType("text/markdown");
        entity.setOriginalFilename(name + ".md");
        entity.setFileSize(100L);
        entity.setFileHash("hash-" + id);
        entity.setVectorStatus(VectorStatus.PENDING);
        return entity;
    }

    // ==================== createWiki ====================

    @Nested
    @DisplayName("createWiki()")
    class CreateWikiTests {

        @Test
        @DisplayName("创建 Wiki 条目并成功向量化")
        void createsWikiWithSuccessfulVectorization() {
            KnowledgeBaseEntity savedEntity = buildEntity(10L, 1L, "测试Wiki");
            savedEntity.setVectorStatus(VectorStatus.COMPLETED);
            when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class))).thenReturn(savedEntity);

            WikiCreateRequest request = new WikiCreateRequest(
                    "测试Wiki",
                    NvcWikiCategory.LEARNING_SUMMARY,
                    NvcWikiSourceType.MANUAL,
                    "Wiki 内容",
                    List.of("tag1"),
                    null
            );

            WikiResponse response = service.createWiki(1L, request);

            assertNotNull(response);
            assertEquals(10L, response.id());
            assertEquals("测试Wiki", response.title());
            verify(vectorService).vectorizeAndStore(eq(10L), eq("Wiki 内容"));
            verify(knowledgeBaseRepository, times(2)).save(any(KnowledgeBaseEntity.class));
        }

        @Test
        @DisplayName("向量化失败时标记 FAILED 状态")
        void marksFailedWhenVectorizationFails() {
            KnowledgeBaseEntity savedEntity = buildEntity(10L, 1L, "测试Wiki");
            when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class))).thenReturn(savedEntity);
            doThrow(new RuntimeException("向量化服务不可用"))
                    .when(vectorService).vectorizeAndStore(anyLong(), anyString());

            WikiCreateRequest request = new WikiCreateRequest(
                    "测试Wiki",
                    NvcWikiCategory.OTHER,
                    NvcWikiSourceType.MANUAL,
                    "Some content",
                    List.of(),
                    null
            );

            WikiResponse response = service.createWiki(1L, request);

            assertNotNull(response);
            assertEquals(VectorStatus.FAILED, savedEntity.getVectorStatus());
            assertNotNull(savedEntity.getVectorError());
        }

        @Test
        @DisplayName("内容为空时跳过向量化")
        void skipsVectorizationWhenContentIsBlank() {
            KnowledgeBaseEntity savedEntity = buildEntity(10L, 1L, "空内容Wiki");
            when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class))).thenReturn(savedEntity);

            WikiCreateRequest request = new WikiCreateRequest(
                    "空内容Wiki",
                    NvcWikiCategory.OTHER,
                    NvcWikiSourceType.MANUAL,
                    null,
                    List.of(),
                    null
            );

            service.createWiki(1L, request);

            verify(vectorService, never()).vectorizeAndStore(anyLong(), anyString());
        }
    }

    // ==================== updateWiki ====================

    @Nested
    @DisplayName("updateWiki()")
    class UpdateWikiTests {

        @Test
        @DisplayName("更新标题和分类")
        void updatesTitleAndCategory() {
            KnowledgeBaseEntity entity = buildEntity(10L, 1L, "旧标题");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class))).thenReturn(entity);

            WikiUpdateRequest request = new WikiUpdateRequest(
                    "新标题", NvcWikiCategory.BOOK_KNOWLEDGE, null, null
            );

            WikiResponse response = service.updateWiki(1L, 10L, request);

            assertEquals("新标题", entity.getName());
            assertEquals(NvcWikiCategory.BOOK_KNOWLEDGE.name(), entity.getCategory());
            verify(vectorService, never()).vectorizeAndStore(anyLong(), anyString());
        }

        @Test
        @DisplayName("内容变更时重新向量化")
        void reVectorizesOnContentChange() {
            KnowledgeBaseEntity entity = buildEntity(10L, 1L, "Wiki");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class))).thenReturn(entity);

            WikiUpdateRequest request = new WikiUpdateRequest(
                    null, null, "新内容", null
            );

            service.updateWiki(1L, 10L, request);

            verify(vectorService).vectorizeAndStore(10L, "新内容");
            assertEquals(VectorStatus.COMPLETED, entity.getVectorStatus());
        }

        @Test
        @DisplayName("重新向量化失败时标记 FAILED")
        void marksFailedWhenReVectorizationFails() {
            KnowledgeBaseEntity entity = buildEntity(10L, 1L, "Wiki");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));
            when(knowledgeBaseRepository.save(any(KnowledgeBaseEntity.class))).thenReturn(entity);
            doThrow(new RuntimeException("vector error"))
                    .when(vectorService).vectorizeAndStore(anyLong(), anyString());

            WikiUpdateRequest request = new WikiUpdateRequest(
                    null, null, "新内容", null
            );

            service.updateWiki(1L, 10L, request);

            assertEquals(VectorStatus.FAILED, entity.getVectorStatus());
            assertEquals("vector error", entity.getVectorError());
        }
    }

    // ==================== deleteWiki ====================

    @Nested
    @DisplayName("deleteWiki()")
    class DeleteWikiTests {

        @Test
        @DisplayName("删除 Wiki 条目并清理向量数据")
        void deletesWikiAndVectorData() {
            KnowledgeBaseEntity entity = buildEntity(10L, 1L, "待删除");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));

            service.deleteWiki(1L, 10L);

            verify(vectorService).deleteByKnowledgeBaseId(10L);
            verify(knowledgeBaseRepository).delete(entity);
        }

        @Test
        @DisplayName("Wiki 不存在时抛出 WIKI_NOT_FOUND")
        void throwsWhenWikiNotFound() {
            when(knowledgeBaseRepository.findById(999L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteWiki(1L, 999L));

            assertEquals(ErrorCode.WIKI_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("无权访问时抛出 WIKI_ACCESS_DENIED")
        void throwsWhenAccessDenied() {
            KnowledgeBaseEntity entity = buildEntity(10L, 2L, "别人的Wiki");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deleteWiki(1L, 10L));

            assertEquals(ErrorCode.WIKI_ACCESS_DENIED.getCode(), ex.getCode());
        }
    }

    // ==================== getWiki ====================

    @Nested
    @DisplayName("getWiki()")
    class GetWikiTests {

        @Test
        @DisplayName("获取单个 Wiki 并验证所有权")
        void getsWikiWithOwnershipValidation() {
            KnowledgeBaseEntity entity = buildEntity(10L, 1L, "我的Wiki");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));

            WikiResponse response = service.getWiki(1L, 10L);

            assertNotNull(response);
            assertEquals(10L, response.id());
            assertEquals("我的Wiki", response.title());
        }

        @Test
        @DisplayName("Wiki 不存在时抛出异常")
        void throwsWhenNotFound() {
            when(knowledgeBaseRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> service.getWiki(1L, 999L));
        }

        @Test
        @DisplayName("访问他人 Wiki 时抛出异常")
        void throwsWhenAccessingOthersWiki() {
            KnowledgeBaseEntity entity = buildEntity(10L, 2L, "他人Wiki");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getWiki(1L, 10L));

            assertEquals(ErrorCode.WIKI_ACCESS_DENIED.getCode(), ex.getCode());
        }
    }

    // ==================== searchWikis ====================

    @Nested
    @DisplayName("searchWikis()")
    class SearchWikisTests {

        @Test
        @DisplayName("语义搜索委托给 ragService")
        void delegatesToRagService() {
            Map<String, Object> metadata = Map.of("kb_id", "10");
            RagResult ragResult = new RagResult("匹配的文本内容", metadata, 0.85);
            when(ragService.retrieve(eq("NVC观察"), eq(List.of(KnowledgeBaseType.PERSONAL_WIKI)),
                    eq(5), eq(1L))).thenReturn(List.of(ragResult));

            KnowledgeBaseEntity entity = buildEntity(10L, 1L, "匹配的Wiki");
            when(knowledgeBaseRepository.findById(10L)).thenReturn(Optional.of(entity));

            List<nvc.guide.modules.nvcwiki.dto.WikiSearchResult> results =
                    service.searchWikis(1L, "NVC观察", 5);

            assertFalse(results.isEmpty());
            assertEquals(1, results.size());
            assertEquals(10L, results.get(0).id());
            assertEquals("匹配的Wiki", results.get(0).title());
            assertEquals(0.85, results.get(0).score(), 0.01);
        }

        @Test
        @DisplayName("无搜索结果时返回空列表")
        void returnsEmptyListWhenNoResults() {
            when(ragService.retrieve(anyString(), anyList(), anyInt(), anyLong()))
                    .thenReturn(List.of());

            List<nvc.guide.modules.nvcwiki.dto.WikiSearchResult> results =
                    service.searchWikis(1L, "不存在的内容", 5);

            assertTrue(results.isEmpty());
        }
    }
}
