package nvc.guide.modules.knowledgebase.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.file.FileStorageService;
import nvc.guide.infrastructure.mapper.KnowledgeBaseMapper;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseListItemDTO;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseStatsDTO;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.knowledgebase.model.RagChatMessageEntity.MessageType;
import nvc.guide.modules.knowledgebase.model.VectorStatus;
import nvc.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import nvc.guide.modules.knowledgebase.repository.RagChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库查询服务
 * 负责知识库列表和详情的查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseListService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagChatMessageRepository ragChatMessageRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileStorageService fileStorageService;

    /**
     * 分页获取知识库列表（支持状态过滤）
     *
     * @param vectorStatus 向量化状态，null 表示不过滤
     * @param pageable 分页参数
     * @return 知识库分页结果
     */
    public Page<KnowledgeBaseListItemDTO> listKnowledgeBases(VectorStatus vectorStatus, Pageable pageable) {
        Page<KnowledgeBaseEntity> page;
        if (vectorStatus != null) {
            page = knowledgeBaseRepository.findByVectorStatusOrderByUploadedAtDesc(vectorStatus, pageable);
        } else {
            page = knowledgeBaseRepository.findAllByOrderByUploadedAtDesc(pageable);
        }
        return page.map(knowledgeBaseMapper::toListItemDTO);
    }

    /**
     * 根据ID获取知识库详情
     */
    public Optional<KnowledgeBaseListItemDTO> getKnowledgeBase(Long id) {
        return knowledgeBaseRepository.findById(id)
            .map(knowledgeBaseMapper::toListItemDTO);
    }

    /**
     * 根据ID获取知识库实体（用于删除等操作）
     */
    public Optional<KnowledgeBaseEntity> getKnowledgeBaseEntity(Long id) {
        return knowledgeBaseRepository.findById(id);
    }

    /**
     * 根据ID列表获取知识库名称列表
     */
    public List<String> getKnowledgeBaseNames(List<Long> ids) {
        Map<Long, KnowledgeBaseEntity> entityMap = knowledgeBaseRepository.findAllById(ids)
            .stream()
            .collect(Collectors.toMap(KnowledgeBaseEntity::getId, Function.identity()));
        return ids.stream()
            .map(id -> Optional.ofNullable(entityMap.get(id))
                .map(KnowledgeBaseEntity::getName)
                .orElse("未知知识库"))
            .toList();
    }

    // ========== 分类管理 ==========

    /**
     * 获取所有分类
     * <p>分类数量有限，不会导致 OOM
     */
    public List<String> getAllCategories() {
        return knowledgeBaseRepository.findAllCategories();
    }

    /**
     * 分页获取分类下的知识库列表
     */
    public Page<KnowledgeBaseListItemDTO> listByCategory(String category, Pageable pageable) {
        Page<KnowledgeBaseEntity> page;
        if (category == null || category.isBlank()) {
            page = knowledgeBaseRepository.findByCategoryIsNullOrderByUploadedAtDesc(pageable);
        } else {
            page = knowledgeBaseRepository.findByCategoryOrderByUploadedAtDesc(category, pageable);
        }
        return page.map(knowledgeBaseMapper::toListItemDTO);
    }

    /**
     * 更新知识库分类
     */
    @Transactional
    public void updateCategory(Long id, String category) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
        entity.setCategory(category != null && !category.isBlank() ? category : null);
        knowledgeBaseRepository.save(entity);
        log.info("更新知识库分类: id={}, category={}", id, category);
    }

    // ========== 类型管理 ==========

    /**
     * 分页获取类型下的知识库列表
     */
    public Page<KnowledgeBaseListItemDTO> listByType(KnowledgeBaseType type, Pageable pageable) {
        Page<KnowledgeBaseEntity> page =
            knowledgeBaseRepository.findByTypeOrderByUploadedAtDesc(type, pageable);
        return page.map(knowledgeBaseMapper::toListItemDTO);
    }

    /**
     * 更新知识库类型
     */
    @Transactional
    public void updateType(Long id, KnowledgeBaseType type) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
        entity.setType(type);
        knowledgeBaseRepository.save(entity);
        log.info("更新知识库类型: id={}, type={}", id, type);
    }

    // ========== 搜索功能 ==========

    /**
     * 分页搜索知识库
     */
    public Page<KnowledgeBaseListItemDTO> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return listKnowledgeBases(null, pageable);
        }
        String escaped = keyword.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return knowledgeBaseRepository.searchByKeyword(escaped, pageable)
            .map(knowledgeBaseMapper::toListItemDTO);
    }

    // ========== 统计功能 ==========

    /**
     * 获取知识库统计信息
     * 总提问次数从用户消息数统计，确保多知识库提问只算一次
     */
    public KnowledgeBaseStatsDTO getStatistics() {
        return new KnowledgeBaseStatsDTO(
            knowledgeBaseRepository.count(),
            ragChatMessageRepository.countByType(MessageType.USER),  // 真正的提问次数
            knowledgeBaseRepository.sumAccessCount(),
            knowledgeBaseRepository.countByVectorStatus(VectorStatus.COMPLETED),
            knowledgeBaseRepository.countByVectorStatus(VectorStatus.PROCESSING)
        );
    }

    // ========== 下载功能 ==========

    /**
     * 下载知识库文件
     */
    public byte[] downloadFile(Long id) {
        KnowledgeBaseEntity entity = knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));

        String storageKey = entity.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "文件存储信息不存在");
        }

        log.info("下载知识库文件: id={}, filename={}", id, entity.getOriginalFilename());
        return fileStorageService.downloadFile(storageKey);
    }

    /**
     * 获取知识库文件信息（用于下载）
     */
    public KnowledgeBaseEntity getEntityForDownload(Long id) {
        return knowledgeBaseRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "知识库不存在"));
    }
}
