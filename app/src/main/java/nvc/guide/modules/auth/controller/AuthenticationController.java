package nvc.guide.modules.auth.controller;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.common.result.Result;
import nvc.guide.common.security.JwtUtil;
import nvc.guide.modules.auth.dto.AuthResponse;
import nvc.guide.modules.auth.dto.LoginRequest;
import nvc.guide.modules.auth.dto.RegisterRequest;
import nvc.guide.modules.auth.model.UserEntity;
import nvc.guide.modules.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 *
 * <p>提供用户注册、登录和获取当前用户信息的 REST API。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        UserEntity user = userService.registerUser(
                request.username(), request.email(), request.password());

        UserDetails userDetails = userService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        log.info("用户注册成功: username={}", user.getUsername());

        return Result.success(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        UserEntity user = userService.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        UserDetails userDetails = userService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        userService.updateLastLoginAt(user.getUsername());
        log.info("用户登录成功: username={}", user.getUsername());

        return Result.success(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    /**
     * 获取当前用户信息（需要在请求头携带 Bearer token）
     *
     * <p>通过 Authorization: Bearer &lt;token&gt; 识别当前用户。
     */
    @GetMapping("/me")
    public Result<AuthResponse> me(jakarta.servlet.http.HttpServletRequest httpRequest) {
        String token = extractBearerToken(httpRequest);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或 token 无效");
        }

        String username = jwtUtil.getUsernameFromToken(token);
        UserEntity user = userService.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));

        return Result.success(AuthResponse.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build());
    }

    /**
     * 从 Authorization 头提取 Bearer token
     */
    private String extractBearerToken(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
