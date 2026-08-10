package nvc.guide.modules.auth.service;

import nvc.guide.modules.auth.model.UserEntity;
import nvc.guide.modules.auth.model.UserRole;
import nvc.guide.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Spring Security UserDetailsService 实现
     * 根据用户名加载用户信息，用于认证流程
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        return new User(
            userEntity.getUsername(),
            userEntity.getPasswordHash(),
            List.of(new SimpleGrantedAuthority("ROLE_" + userEntity.getRole().name()))
        );
    }

    /**
     * 注册新用户
     *
     * @param username 用户名（唯一）
     * @param email    邮箱（唯一）
     * @param password 明文密码（将被 BCrypt 加密存储）
     * @return 创建的用户实体
     * @throws IllegalArgumentException 用户名或邮箱已存在
     */
    @Transactional
    public UserEntity registerUser(String username, String email, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已存在: " + email);
        }

        UserEntity user = UserEntity.builder()
            .username(username)
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .role(UserRole.USER)
            .build();

        UserEntity saved = userRepository.save(user);
        log.info("用户注册成功: username={}", username);
        return saved;
    }

    /**
     * 根据用户名查找用户
     */
    @Transactional(readOnly = true)
    public Optional<UserEntity> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 更新最后登录时间
     */
    @Transactional
    public void updateLastLoginAt(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
