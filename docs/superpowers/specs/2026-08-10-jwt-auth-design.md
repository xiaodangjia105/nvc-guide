# JWT 认证体系设计文档

**日期：** 2026-08-10
**范围：** 用户认证和授权
**分支：** `feat/jwt-authentication`

---

## 背景

当前系统没有真正的认证机制：
- 前端使用随机 6 位数作为 userId，存储在 localStorage
- 后端从 `@RequestParam` 获取 userId，任何人都可以冒充其他用户
- Trace/Metrics API 无鉴权（已添加 API Key 保护）

**目标：** 实现基于 JWT 的用户认证体系，保护用户数据安全。

---

## 技术选型

| 组件 | 选择 | 原因 |
|------|------|------|
| 认证方式 | JWT (JSON Web Token) | 无状态、前后端分离友好 |
| 密码哈希 | BCrypt | Spring Security 内置支持 |
| Token 存储 | localStorage | 简单，兼容现有架构 |
| 安全框架 | Spring Security 6 | Spring Boot 4.0 标准 |

---

## 架构设计

### 后端架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Spring Security                        │
├─────────────────────────────────────────────────────────────┤
│  JwtAuthenticationFilter                                     │
│    ↓                                                         │
│  SecurityConfig (filterChain)                                │
│    ↓                                                         │
│  AuthenticationController (register/login/refresh)           │
│    ↓                                                         │
│  UserService (UserDetailsService implementation)             │
│    ↓                                                         │
│  UserRepository (Spring Data JPA)                            │
└─────────────────────────────────────────────────────────────┘
```

### 前端架构

```
┌─────────────────────────────────────────────────────────────┐
│                      React App                               │
├─────────────────────────────────────────────────────────────┤
│  AuthContext (token 管理)                                     │
│    ↓                                                         │
│  LoginPage / RegisterPage                                    │
│    ↓                                                         │
│  axios interceptor (自动添加 Authorization header)           │
│    ↓                                                         │
│  ProtectedRoute (路由守卫)                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 数据模型

### User Entity

```java
@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.USER;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}

public enum UserRole {
    USER, ADMIN
}
```

### JWT Token 结构

```json
{
  "sub": "123",
  "username": "user123",
  "role": "USER",
  "iat": 1691234567,
  "exp": 1691320967
}
```

---

## API 设计

### 认证端点

| 方法 | 路径 | 描述 | 认证要求 |
|------|------|------|----------|
| POST | `/api/auth/register` | 用户注册 | 无 |
| POST | `/api/auth/login` | 用户登录 | 无 |
| POST | `/api/auth/refresh` | 刷新 Token | 需要 Token |
| GET | `/api/auth/me` | 获取当前用户信息 | 需要 Token |

### 请求/响应格式

**注册请求：**
```json
{
  "username": "user123",
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**登录请求：**
```json
{
  "username": "user123",
  "password": "securePassword123"
}
```

**成功响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "username": "user123",
      "email": "user@example.com",
      "role": "USER"
    }
  }
}
```

---

## 安全配置

### 端点保护规则

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/nvc/traces/**", "/api/nvc/metrics/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### JWT 配置

```yaml
app:
  security:
    jwt:
      secret: ${JWT_SECRET:your-256-bit-secret-key-here}
      expiration: 86400000  # 24 hours
      refresh-expiration: 604800000  # 7 days
```

---

## 前端改动

### 1. AuthContext

```typescript
interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
}
```

### 2. Axios Interceptor

```typescript
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
```

### 3. ProtectedRoute

```typescript
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  if (!isAuthenticated) {
    return <Navigate to="/login" />;
  }
  return <>{children}</>;
}
```

---

## 迁移策略

### 阶段 1：后端实现（2-3 天）
1. 添加 Spring Security 依赖
2. 创建 User Entity 和 Repository
3. 实现 JWT 工具类
4. 创建 AuthenticationController
5. 配置 SecurityFilterChain

### 阶段 2：前端实现（2-3 天）
1. 创建 AuthContext
2. 实现 LoginPage 和 RegisterPage
3. 添加 axios interceptor
4. 修改 useUserId 为 useAuth
5. 更新所有 API 调用使用 token

### 阶段 3：数据迁移（1 天）
1. 创建 users 表
2. 为现有数据创建默认用户
3. 更新现有记录关联到默认用户

---

## 向后兼容

- 现有 API 保持兼容，通过 `@RequestParam userId` 的调用方式在迁移期间仍然有效
- 前端渐进式迁移，先实现登录，再逐步替换 userId 参数
- 提供迁移脚本，将现有数据关联到默认用户

---

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| JWT Secret 泄露 | 高 — 所有 token 失效 | 使用环境变量，定期轮换 |
| 密码哈希性能 | 中 — BCrypt 计算慢 | 使用异步处理 |
| 前端迁移复杂 | 中 — 需要修改所有 API 调用 | 渐进式迁移，保持兼容 |
| 现有数据丢失 | 高 — 用户数据关联失败 | 迁移脚本 + 备份 |

---

## 测试计划

### 单元测试
- JWT 工具类测试
- UserService 测试
- AuthenticationController 测试

### 集成测试
- 注册流程测试
- 登录流程测试
- Token 刷新测试
- 受保护端点访问测试

### E2E 测试
- 完整登录流程
- Token 过期处理
- 权限验证

---

## 总结

JWT 认证体系将解决以下问题：
1. ✅ 用户身份验证 — 只有注册用户才能访问系统
2. ✅ 用户数据隔离 — 用户只能访问自己的数据
3. ✅ API 安全 — 所有 API 端点都需要认证
4. ✅ 角色权限 — ADMIN 角色可以访问 Trace/Metrics API

**预计工期：** 5-7 天
**优先级：** P0 — 安全基础
