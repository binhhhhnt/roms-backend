package com.roms.security;

import com.roms.entity.RefreshToken;
import com.roms.entity.User;
import com.roms.repository.RefreshTokenRepository;
import com.roms.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Bộ lọc xác thực JWT kế thừa OncePerRequestFilter (chạy duy nhất 1 lần cho mỗi HTTP request).
 * Luồng hoạt động:
 * 1. Trích xuất Bearer Token từ header "Authorization".
 * 2. Validate token bằng JwtTokenProvider (kiểm tra chữ ký và hạn dùng).
 * 3. Trích xuất userId và sessionId từ token.
 * 4. Kiểm tra User trong database (phải tồn tại và đang hoạt động isActive = true).
 * 5. Kiểm tra Session trong database (phải chưa bị thu hồi revoked_at IS NULL và còn hạn).
 * 6. Tạo đối tượng Authentication (UserPrincipal) và nạp vào SecurityContextHolder.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1. Trích xuất token từ header
        String token = extractToken(request);

        // 2. Validate token JWT
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                Long sessionId = jwtTokenProvider.getSessionId(token);

                // 3. Kiểm tra User tồn tại và đang kích hoạt (isActive = true)
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent() && Boolean.TRUE.equals(userOpt.get().getIsActive())) {
                    User user = userOpt.get();

                    // 4. Kiểm tra phiên làm việc trong DB: Chưa bị thu hồi và chưa hết hạn
                    Optional<RefreshToken> sessionOpt = refreshTokenRepository.findByIdAndRevokedAtIsNull(sessionId);
                    if (sessionOpt.isPresent()) {
                        RefreshToken session = sessionOpt.get();
                        if (session.getExpiresAt().isAfter(LocalDateTime.now())) {
                            // 5. Khởi tạo UserPrincipal và xác thực thành công vào SecurityContext
                            UserPrincipal principal = UserPrincipal.create(user, sessionId);
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    principal,
                                    null,
                                    principal.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        } else {
                            log.debug("Session id {} has expired at {}", sessionId, session.getExpiresAt());
                        }
                    } else {
                        log.debug("Session id {} is revoked or not found in PostgreSQL", sessionId);
                    }
                } else {
                    log.debug("User id {} not found or inactive", userId);
                }
            } catch (Exception ex) {
                log.error("Could not set user authentication in security context: {}", ex.getMessage());
            }
        }

        // Chuyển tiếp request sang filter tiếp theo trong chuỗi filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Helper method: Cắt chuỗi "Bearer " để lấy token JWT nguyên bản.
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
