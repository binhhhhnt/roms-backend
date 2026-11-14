package com.roms.service.impl;

import com.roms.enums.UserRole;
import com.roms.exception.*;
import com.roms.security.UserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class CatalogAccessTest {
    @BeforeEach @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    @Test @DisplayName("Catalog access: unauthenticated caller is rejected")
    void anonymousRejected() {
        assertThatThrownBy(CatalogAccess::requireOwner).isInstanceOf(UnauthorizedException.class);
    }
    @Test @DisplayName("Catalog access: owner can manage menu and categories")
    void ownerAccepted() {
        var principal = UserPrincipal.builder().id(1L).role(UserRole.OWNER).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        assertThatCode(CatalogAccess::requireOwner).doesNotThrowAnyException();
    }
    @Test @DisplayName("Catalog access: every non-owner role is forbidden")
    void staffAndTabletRejected() {
        for(var role : new UserRole[]{UserRole.SERVICE_STAFF,UserRole.KITCHEN_STAFF,UserRole.INVENTORY_STAFF,UserRole.TABLE_ACCOUNT}) {
            var principal = UserPrincipal.builder().id(7L).role(role).build();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
            assertThatThrownBy(CatalogAccess::requireOwner).isInstanceOf(ForbiddenException.class);
        }
    }
}
