package com.roms.security;

import com.roms.enums.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class SecurityUtilsTest {
    @BeforeEach @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    @Test @DisplayName("Security context: absent authentication returns no user or session")
    void absentAuthentication() {
        assertThat(SecurityUtils.getCurrentUserId()).isEmpty();
        assertThat(SecurityUtils.getCurrentSessionId()).isEmpty();
        assertThat(SecurityUtils.getCurrentUserRole()).isEmpty();
    }
    @Test @DisplayName("Security context: string or unauthenticated principals are not trusted")
    void untrustedPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymous",""));
        assertThat(SecurityUtils.getCurrentUserPrincipal()).isEmpty();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymous","",java.util.List.of()));
        assertThat(SecurityUtils.getCurrentUserPrincipal()).isEmpty();
    }
    @Test @DisplayName("Security context: authenticated table principal exposes its own user table role and session")
    void authenticatedTable() {
        var principal = UserPrincipal.builder().id(7L).tableId(3L).sessionId(9L).role(UserRole.TABLE_ACCOUNT).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        assertThat(SecurityUtils.getCurrentUserId()).contains(7L);
        assertThat(SecurityUtils.getCurrentTableId()).contains(3L);
        assertThat(SecurityUtils.getCurrentSessionId()).contains(9L);
        assertThat(SecurityUtils.getCurrentUserRole()).contains(UserRole.TABLE_ACCOUNT);
    }
}
