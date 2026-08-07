package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcSemanticCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NvcSemanticCacheRepository extends JpaRepository<NvcSemanticCacheEntity, Long> {

    /**
     * 向量相似度搜索（pgvector cosine distance）
     * 返回相似度 > (1 - threshold) 的缓存条目，按相似度降序
     */
    @Query(value = "SELECT *, 1 - (query_embedding <=> CAST(:embedding AS vector)) AS score " +
           "FROM nvc_semantic_cache " +
           "WHERE 1 - (query_embedding <=> CAST(:embedding AS vector)) > :threshold " +
           "AND (expires_at IS NULL OR expires_at > NOW()) " +
           "ORDER BY query_embedding <=> CAST(:embedding AS vector) " +
           "LIMIT :limit",
           nativeQuery = true)
    List<NvcSemanticCacheEntity> findBySimilarity(
        @Param("embedding") String embedding,
        @Param("threshold") double threshold,
        @Param("limit") int limit);

    /**
     * 清理过期缓存
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NvcSemanticCacheEntity c WHERE c.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    /**
     * 统计缓存命中次数
     */
    @Query("SELECT SUM(c.hitCount) FROM NvcSemanticCacheEntity c")
    Long getTotalHitCount();

    /**
     * 统计缓存条目数
     */
    long count();
}
