package nvc.guide.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 *
 * <p>基于 jjwt 0.12.x，负责 token 的生成、解析、校验。
 * 密钥和过期时间从 application.yml 读取：
 * <pre>
 * app:
 *   jwt:
 *     secret: ${JWT_SECRET:...}
 *     expiration: ${JWT_EXPIRATION:86400000}
 * </pre>
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * 生成 JWT token
     *
     * @param userDetails Spring Security 用户信息
     * @return 签名后的 JWT 字符串
     */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 验证 token 是否有效（签名正确且未过期）
     *
     * @param token JWT 字符串
     * @return true = 有效；false = 无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 token 中提取用户名（subject）
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 检查 token 是否已过期
     *
     * @param token JWT 字符串
     * @return true = 已过期；false = 未过期
     */
    public boolean isTokenExpired(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 解析并验证 token，返回 Claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
