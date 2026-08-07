package nvc.guide.modules.nvcpractice.model;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/**
 * 语义缓存实体
 * 基于向量相似度的 LLM 响应缓存，减少重复调用
 */
@Entity
@Table(name = "nvc_semantic_cache")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcSemanticCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    /**
     * 查询向量（pgvector 格式，由 EmbeddingModel 生成）
     * 存储为字符串格式 "[0.1,0.2,...]"，由 Repository 层转换
     */
    @Column(name = "query_embedding", nullable = false, columnDefinition = "vector(1024)")
    private String queryEmbedding;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;

    /**
     * Agent 场景（仅缓存知识问答类场景）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", length = 50)
    private NvcAgentScene agentScene;

    @Column(name = "hit_count")
    @Builder.Default
    private Integer hitCount = 0;

    @Column(name = "last_hit_at")
    private LocalDateTime lastHitAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
