package nvc.guide.modules.knowledgebase.service;

import nvc.guide.modules.knowledgebase.listener.VectorizeStreamProducer;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import nvc.guide.modules.knowledgebase.model.KnowledgeBaseType;
import nvc.guide.modules.knowledgebase.model.VectorStatus;
import nvc.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * NVC 知识库种子数据服务
 * 应用启动时自动检查并初始化 NVC 知识文档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedKnowledgeBaseService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final VectorizeStreamProducer vectorizeStreamProducer;

    /**
     * 文件夹名称 → 知识库类型映射
     */
    private static final Map<String, KnowledgeBaseType> FOLDER_TO_TYPE = Map.of(
        "theory",     KnowledgeBaseType.NVC_THEORY,
        "vocabulary", KnowledgeBaseType.EMOTION_VOCAB,
        "templates",  KnowledgeBaseType.SPEECH_TEMPLATE,
        "cases",      KnowledgeBaseType.USER_CASE
    );

    /**
     * 需要检查的知识库类型
     */
    private static final List<KnowledgeBaseType> NVC_TYPES = List.of(
        KnowledgeBaseType.NVC_THEORY,
        KnowledgeBaseType.SPEECH_TEMPLATE,
        KnowledgeBaseType.EMOTION_VOCAB,
        KnowledgeBaseType.USER_CASE
    );

    /**
     * 应用启动时检查并执行种子数据
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkAndSeed() {
        try {
            // 检查是否已有 NVC 类型的知识库
            List<KnowledgeBaseEntity> existingDocs = knowledgeBaseRepository
                .findByTypeInOrderByUploadedAtDesc(NVC_TYPES);

            if (existingDocs.isEmpty()) {
                log.info("NVC knowledge base not found, starting seed...");
                seedKnowledgeDocuments();
                return;
            }

            // 检查是否有向量化失败的文档，自动重试
            List<KnowledgeBaseEntity> failedDocs = existingDocs.stream()
                .filter(doc -> doc.getVectorStatus() == VectorStatus.FAILED)
                .toList();

            if (!failedDocs.isEmpty()) {
                log.info("Found {} failed vectorization docs, retrying...", failedDocs.size());
                retryFailedVectorization(failedDocs);
            } else {
                log.info("NVC knowledge base already seeded ({} docs), all vectorized", existingDocs.size());
            }
        } catch (Exception e) {
            log.error("Failed to seed NVC knowledge base", e);
        }
    }

    /**
     * 重试向量化失败的种子文档
     * 从 classpath 重新读取内容并发送向量化任务
     */
    private void retryFailedVectorization(List<KnowledgeBaseEntity> failedDocs) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        int retryCount = 0;
        int skipCount = 0;

        // 构建 filename → type 的映射
        Map<String, KnowledgeBaseType> filenameToType = new java.util.HashMap<>();
        for (Map.Entry<String, KnowledgeBaseType> entry : FOLDER_TO_TYPE.entrySet()) {
            try {
                Resource[] resources = resolver.getResources("classpath:knowledge/" + entry.getKey() + "/*.md");
                for (Resource resource : resources) {
                    String filename = resource.getFilename();
                    if (filename != null) {
                        filenameToType.put(filename, entry.getValue());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to scan folder for retry: {}", entry.getKey(), e);
            }
        }

        for (KnowledgeBaseEntity doc : failedDocs) {
            String filename = doc.getOriginalFilename();
            KnowledgeBaseType type = filenameToType.get(filename);

            if (type == null) {
                log.warn("Cannot retry vectorization: unknown type for {}", filename);
                skipCount++;
                continue;
            }

            try {
                // 从 classpath 读取内容
                String resourcePath = "classpath:knowledge/" + getTypeFolder(type) + "/" + filename;
                Resource resource = resolver.getResource(resourcePath);

                if (!resource.exists()) {
                    log.warn("Cannot retry vectorization: resource not found: {}", resourcePath);
                    skipCount++;
                    continue;
                }

                String content;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    content = reader.lines().collect(Collectors.joining("\n"));
                }

                if (content.isBlank()) {
                    log.warn("Cannot retry vectorization: empty content: {}", filename);
                    skipCount++;
                    continue;
                }

                // 重置向量化状态
                doc.setVectorStatus(VectorStatus.PENDING);
                doc.setVectorError(null);
                knowledgeBaseRepository.save(doc);

                // 重新发送向量化任务
                vectorizeStreamProducer.sendVectorizeTask(doc.getId(), content);
                log.info("Retried vectorization: kbId={}, name={}", doc.getId(), doc.getName());
                retryCount++;
            } catch (Exception e) {
                log.error("Failed to retry vectorization: kbId={}, name={}", doc.getId(), doc.getName(), e);
                skipCount++;
            }
        }

        log.info("Vectorization retry completed: retried={}, skipped={}", retryCount, skipCount);
    }

    /**
     * 获取知识库类型对应的文件夹名称
     */
    private String getTypeFolder(KnowledgeBaseType type) {
        return switch (type) {
            case NVC_THEORY -> "theory";
            case EMOTION_VOCAB -> "vocabulary";
            case SPEECH_TEMPLATE -> "templates";
            case USER_CASE -> "cases";
            case PERSONAL_WIKI -> "personal";
        };
    }

    /**
     * 扫描 resources/knowledge/ 目录并注册知识文档
     */
    private void seedKnowledgeDocuments() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<String, KnowledgeBaseType> entry : FOLDER_TO_TYPE.entrySet()) {
            String folder = entry.getKey();
            KnowledgeBaseType type = entry.getValue();

            try {
                Resource[] resources = resolver.getResources("classpath:knowledge/" + folder + "/*.md");

                for (Resource resource : resources) {
                    try {
                        seedSingleDocument(resource, type);
                        successCount++;
                    } catch (Exception e) {
                        log.error("Failed to seed document: {}", resource.getFilename(), e);
                        failCount++;
                    }
                }
            } catch (Exception e) {
                log.error("Failed to scan folder: {}", folder, e);
                failCount++;
            }
        }

        log.info("NVC knowledge base seed completed: success={}, failed={}", successCount, failCount);
    }

    /**
     * 注册单个知识文档
     */
    private void seedSingleDocument(Resource resource, KnowledgeBaseType type) throws Exception {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(".md")) {
            return;
        }

        // 读取文件内容
        String content;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            content = reader.lines().collect(Collectors.joining("\n"));
        }

        if (content.isBlank()) {
            log.warn("Skipping empty document: {}", filename);
            return;
        }

        // 计算文件哈希
        String fileHash = calculateHash(content);

        // 检查是否已存在（幂等）
        if (knowledgeBaseRepository.existsByFileHash(fileHash)) {
            log.debug("Document already exists, skipping: {}", filename);
            return;
        }

        // 创建知识库实体
        String name = filename.replace(".md", "");
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setFileHash(fileHash);
        kb.setName(name);
        kb.setOriginalFilename(filename);
        kb.setCategory(type.name());
        kb.setType(type);
        kb.setFileSize((long) content.getBytes(StandardCharsets.UTF_8).length);
        kb.setContentType("text/markdown");
        kb.setStorageKey("seed/" + type.name().toLowerCase() + "/" + filename);
        kb.setStorageUrl("");
        kb.setUploadedAt(LocalDateTime.now());
        kb.setLastAccessedAt(LocalDateTime.now());
        kb.setAccessCount(0);
        kb.setQuestionCount(0);
        kb.setVectorStatus(VectorStatus.PENDING);
        kb.setChunkCount(0);

        // 保存到数据库
        KnowledgeBaseEntity saved = knowledgeBaseRepository.save(kb);
        log.info("Registered NVC knowledge document: id={}, name={}, type={}",
            saved.getId(), name, type);

        // 发送向量化任务
        vectorizeStreamProducer.sendVectorizeTask(saved.getId(), content);
        log.info("Vectorization task queued: kbId={}, name={}", saved.getId(), name);
    }

    /**
     * 计算内容的 SHA-256 哈希
     */
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate hash", e);
        }
    }
}
