package com.roms.config;

import com.roms.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cấu hình Spring Security chính của ứng dụng ROMS Backend.
 * Thiết lập:
 * - Cơ chế bảo mật JWT Stateless (không dùng HttpSession).
 * - Tắt CSRF (vì kiến trúc là API REST Stateless dùng Token Bearer).
 * - Cấu hình CORS cho phép các client được chỉ định truy cập.
 * - Xử lý ngoại lệ 401 Unauthorized và 403 Forbidden chuẩn JSON API.
 * - Phân quyền truy cập các đường dẫn endpoint (Public vs Protected).
 * - Đăng ký bộ lọc JwtAuthenticationFilter trước UsernamePasswordAuthenticationFilter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Bean mã hóa mật khẩu người dùng bằng thuật toán BCrypt.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Bean quản lý xác thực của Spring Security.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Cấu hình chuỗi lọc bảo mật (SecurityFilterChain).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Tắt CSRF vì ứng dụng dùng kiến trúc RESTful JWT Stateless
                .csrf(AbstractHttpConfigurer::disable)
                // Kích hoạt CORS
                .cors(cors -> {})
                // Thiết lập phiên Stateless: Spring Security không tạo hay lưu trữ HttpSession
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Xử lý các lỗi bảo mật và trả về JSON theo chuẩn ApiResponse
                .exceptionHandling(exceptions -> exceptions
                        // 401 Unauthorized: Khi chưa đăng nhập hoặc token không hợp lệ
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"Authentication required\",\"error\":{\"code\":\"UNAUTHORIZED\",\"details\":null}}");
                        })
                        // 403 Forbidden: Khi đã đăng nhập nhưng không đủ quyền hạn (Role)
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"success\":false,\"message\":\"Access denied\",\"error\":{\"code\":\"FORBIDDEN\",\"details\":null}}");
                        })
                )
                // Phân quyền cho từng nhóm endpoint HTTP
                .authorizeHttpRequests(auth -> auth
                        // Các endpoint công khai không yêu cầu token: login, refresh, quên mật khẩu
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/forgot-password",
                                "/api/auth/verify-otp",
                                "/api/auth/reset-password"
                        ).permitAll()
                        // Cho phép actuator health check và đường dẫn lỗi
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        // Tất cả các endpoint /api/** còn lại đều bắt buộc phải đăng nhập có Token
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                // Chèn JwtAuthenticationFilter vào trước bộ lọc mặc định của Spring Security
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
