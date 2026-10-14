package com.roms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Component phụ trách sinh (generate), giải mã (parse), trích xuất thông tin (extract)
 * và kiểm tra tính hợp lệ (validate) của JSON Web Token (JWT).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Khởi tạo khóa bí mật SecretKey từ chuỗi cấu hình sau khi Bean được tạo.
     * Khóa bí mật phải đạt độ dài tối thiểu 256 bits (32 bytes) để đảm bảo an toàn cho thuật toán HS256.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret key must be at least 256 bits (32 bytes)");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Sinh JWT Access Token với thời gian sống ngắn (mặc định 15 phút).
     * Nhúng các claims:
     * - subject: userId (ID người dùng)
     * - role: Vai trò người dùng (OWNER, CASHIER, WAITER,...)
     * - sid: Session ID trong cơ sở dữ liệu
     *
     * @param userId    ID người dùng
     * @param role      tên vai trò
     * @param sessionId ID của phiên làm việc
     * @return chuỗi JWT đã ký
     */
    public String generateAccessToken(Long userId, String role, Long sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpirationSeconds());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("sid", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Quá tải tiện ích sinh token nhận UserRole enum.
     */
    public String generateAccessToken(Long userId, com.roms.enums.UserRole role, Long sessionId) {
        return generateAccessToken(userId, role != null ? role.name() : null, sessionId);
    }

    /**
     * Giải mã và trích xuất Claims từ chuỗi JWT.
     * Ném ra ngoại lệ nếu chữ ký không hợp lệ hoặc token đã hết hạn.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Kiểm tra tính hợp lệ của token (chữ ký đúng và chưa hết hạn).
     *
     * @param token chuỗi JWT
     * @return true nếu hợp lệ, false nếu hết hạn hoặc sai chữ ký
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Trích xuất userId từ claim "sub" (Subject).
     */
    public Long getUserId(String token) {
        String sub = parseClaims(token).getSubject();
        return Long.parseLong(sub);
    }

    /**
     * Trích xuất role từ claim "role".
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * Trích xuất sessionId từ claim "sid".
     */
    public Long getSessionId(String token) {
        Object sid = parseClaims(token).get("sid");
        if (sid instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(sid.toString());
    }
}
