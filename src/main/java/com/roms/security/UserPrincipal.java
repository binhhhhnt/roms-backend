package com.roms.security;

import com.roms.entity.User;
import com.roms.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Triển khai UserDetails của Spring Security:
 * Đóng gói toàn bộ thông tin của người dùng đang thực hiện request để lưu trữ trong SecurityContext.
 * Cung cấp Role dưới dạng GrantedAuthority ("ROLE_" + role.name()).
 */
@Getter
@Builder
public class UserPrincipal implements UserDetails {

    public UserPrincipal(Long id, String username, String password, String fullName,
                         UserRole role, Long tableId, Long sessionId, boolean active) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.tableId = tableId;
        this.sessionId = sessionId;
        this.active = active;
    }

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final UserRole role;
    private final Long tableId;
    private final Long sessionId;
    private final boolean active;

    /**
     * Factory method tạo UserPrincipal từ User Entity và Session ID.
     */
    public static UserPrincipal create(User user, Long sessionId) {
        Long tableId = (user.getTable() != null) ? user.getTable().getId() : null;
        return UserPrincipal.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .fullName(user.getFullName())
                .role(user.getRole())
                .tableId(tableId)
                .sessionId(sessionId)
                .active(Boolean.TRUE.equals(user.getIsActive()))
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
