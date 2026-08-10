package nvc.guide.modules.auth.dto;

import lombok.Builder;

/**
 * 认证响应（登录/注册成功后返回）
 */
@Builder
public record AuthResponse(
        String token,
        String username,
        String email,
        String role
) {}
