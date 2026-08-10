package nvc.guide.modules.knowledgebase.repository;

import nvc.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.knowledgebase.model.VectorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 知识库Repository
 */
@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    /**
     * 根据文件哈希查找知识库（用于去重）
     */
    Optional<KnowledgeBaseEntity> findByFileHash(String fileHash);

    /**
     * 检查文件哈希是否存在
     */
    boolean existsByFileHash(String fileHash);

    /**
     * 按上传时间倒序查找所有知识库
     * @deprecated 使用 {@link #findAllByOrderByUploadedAtDesc(Pageable)} 分页查询，避免数据量增长后 OOM
     */
    @Deprecated
    List<KnowledgeBaseEntity> findAllByOrderByUploadedAtDesc();

    /**
     * 按上传时间倒序分页查找所有知识库
     */
    Page<KnowledgeBaseEntity> findAllByOrderByUploadedAtDesc(Pageable pageable);

    /**
     * 获取所有不同的分类
     * <p>分类数量有限，不会导致 OOM，无需分页
     */
    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k WHERE k.category IS NOT NULL ORDER BY k.category")
    List<String> findAllCategories();

    /**
     * 根据分类查找知识库
     * @deprecated 使用 {@link #findByCategoryOrderByUploadedAtDesc(String, Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findByCategoryOrderByUploadedAtDesc(String category);

    /**
     * 根据分类分页查找知识库
     */
    Page<KnowledgeBaseEntity> findByCategoryOrderByUploadedAtDesc(String category, Pageable pageable);

    /**
     * 查找未分类的知识库
     * @deprecated 使用 {@link #findByCategoryIsNullOrderByUploadedAtDesc(Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findByCategoryIsNullOrderByUploadedAtDesc();

    /**
     * 分页查找未分类的知识库
     */
    Page<KnowledgeBaseEntity> findByCategoryIsNullOrderByUploadedAtDesc(Pageable pageable);

    /**
     * 按名称或文件名模糊搜索（不区分大小写）
     * @deprecated 使用 {@link #searchByKeyword(String, Pageable)} 分页查询
     */
    @Deprecated
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY k.uploadedAt DESC")
    List<KnowledgeBaseEntity> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 按名称或文件名模糊搜索（分页）
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY k.uploadedAt DESC")
    Page<KnowledgeBaseEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 按文件大小排序
     * @deprecated 使用 {@link #findAllByOrderByFileSizeDesc(Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findAllByOrderByFileSizeDesc();

    /**
     * 按文件大小分页排序
     */
    Page<KnowledgeBaseEntity> findAllByOrderByFileSizeDesc(Pageable pageable);

    /**
     * 按访问次数排序
     * @deprecated 使用 {@link #findAllByOrderByAccessCountDesc(Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findAllByOrderByAccessCountDesc();

    /**
     * 按访问次数分页排序
     */
    Page<KnowledgeBaseEntity> findAllByOrderByAccessCountDesc(Pageable pageable);

    /**
     * 按提问次数排序
     * @deprecated 使用 {@link #findAllByOrderByQuestionCountDesc(Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findAllByOrderByQuestionCountDesc();

    /**
     * 按提问次数分页排序
     */
    Page<KnowledgeBaseEntity> findAllByOrderByQuestionCountDesc(Pageable pageable);

    // ==================== 批量更新 ====================

    /**
     * 批量增加知识库提问计数
     * @param ids 知识库ID列表
     * @return 更新的行数
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount + 1 WHERE k.id IN :ids")
    int incrementQuestionCountBatch(@Param("ids") List<Long> ids);

    // ==================== 统计查询 ====================

    /**
     * 统计总提问次数
     */
    @Query("SELECT COALESCE(SUM(k.questionCount), 0) FROM KnowledgeBaseEntity k")
    long sumQuestionCount();

    /**
     * 统计总访问次数
     */
    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k")
    long sumAccessCount();

    /**
     * 按向量化状态统计数量
     */
    long countByVectorStatus(VectorStatus vectorStatus);

    /**
     * 按向量化状态查找知识库（按上传时间倒序）
     * @deprecated 使用 {@link #findByVectorStatusOrderByUploadedAtDesc(VectorStatus, Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findByVectorStatusOrderByUploadedAtDesc(VectorStatus vectorStatus);

    /**
     * 按向量化状态分页查找知识库（按上传时间倒序）
     */
    Page<KnowledgeBaseEntity> findByVectorStatusOrderByUploadedAtDesc(VectorStatus vectorStatus, Pageable pageable);

    // ==================== 类型查询 ====================

    /**
     * 按类型查找知识库
     * @deprecated 使用 {@link #findByTypeOrderByUploadedAtDesc(KnowledgeBaseType, Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findByTypeOrderByUploadedAtDesc(KnowledgeBaseType type);

    /**
     * 按类型分页查找知识库
     */
    Page<KnowledgeBaseEntity> findByTypeOrderByUploadedAtDesc(KnowledgeBaseType type, Pageable pageable);

    /**
     * 按类型列表查找知识库
     * @deprecated 使用 {@link #findByTypeInOrderByUploadedAtDesc(List, Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findByTypeInOrderByUploadedAtDesc(List<KnowledgeBaseType> types);

    /**
     * 按类型列表分页查找知识库
     */
    Page<KnowledgeBaseEntity> findByTypeInOrderByUploadedAtDesc(List<KnowledgeBaseType> types, Pageable pageable);

    // ==================== 用户 Wiki 查询 ====================

    /**
     * 按类型 + 用户 ID 查找（PERSONAL_WIKI 专用）
     * @deprecated 使用 {@link #findByTypeAndUserIdOrderByUploadedAtDesc(KnowledgeBaseType, Long, Pageable)} 分页查询
     */
    @Deprecated
    List<KnowledgeBaseEntity> findByTypeAndUserIdOrderByUploadedAtDesc(
            KnowledgeBaseType type, Long userId);

    /**
     * 按类型 + 用户 ID + 关键词搜索
     * @deprecated 使用 {@link #searchByTypeAndUserIdAndKeyword(KnowledgeBaseType, Long, String, Pageable)} 分页查询
     */
    @Deprecated
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.type = :type AND k.userId = :userId " +
           "AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(k.category) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<KnowledgeBaseEntity> searchByTypeAndUserIdAndKeyword(
            @Param("type") KnowledgeBaseType type,
            @Param("userId") Long userId,
            @Param("keyword") String keyword);

    /**
     * 按类型 + 用户 ID + 关键词分页搜索
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.type = :type AND k.userId = :userId " +
           "AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(k.category) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<KnowledgeBaseEntity> searchByTypeAndUserIdAndKeyword(
            @Param("type") KnowledgeBaseType type,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 统计用户 Wiki 数量
     */
    long countByTypeAndUserId(KnowledgeBaseType type, Long userId);

    /**
     * 按类型 + 用户 ID 分页查询
     */
    Page<KnowledgeBaseEntity> findByTypeAndUserIdOrderByUploadedAtDesc(
            KnowledgeBaseType type, Long userId, Pageable pageable);

    /**
     * 按类型 + 用户 ID + 分类筛选分页查询
     */
    Page<KnowledgeBaseEntity> findByTypeAndUserIdAndCategoryOrderByUploadedAtDesc(
            KnowledgeBaseType type, Long userId, String category, Pageable pageable);
}
