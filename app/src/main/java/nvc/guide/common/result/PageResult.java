package nvc.guide.common.result;

import lombok.Getter;

import java.util.List;

/**
 * 分页响应结果
 *
 * <p>匹配 API 契约 docs/contracts/api.md 中的分页格式。
 */
@Getter
public class PageResult<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int number;
    private final int size;

    private PageResult(List<T> content, long totalElements, int totalPages,
                       int number, int size) {
        this.content = content;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.number = number;
        this.size = size;
    }

    /**
     * 从 Spring Data Page 构建 PageResult
     */
    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(
            page.getContent(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize()
        );
    }

    /**
     * 手动构建 PageResult（用于非 JPA 分页场景）
     */
    public static <T> PageResult<T> of(List<T> content, long totalElements,
                                       int page, int size) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResult<>(content, totalElements, totalPages, page, size);
    }
}
