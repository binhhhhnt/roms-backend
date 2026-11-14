package com.roms.service;

import com.roms.dto.request.*;
import com.roms.entity.*;
import com.roms.enums.*;
import com.roms.exception.*;
import com.roms.mapper.UserMapper;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.UserServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {
    @Mock UserRepository users;
    @Mock RestaurantTableRepository tables;
    @Mock ServiceSessionRepository tableSessions;
    @Mock RefreshTokenService sessions;
    @Mock PasswordEncoder encoder;
    UserServiceImpl service;
    User user;
    RestaurantTable table;
    @BeforeEach void setUp() {
        SecurityContextHolder.clearContext();
        service = new UserServiceImpl(users,tables,tableSessions,sessions,encoder,new UserMapper(tableSessions));
        user = User.builder().id(7L).username("staff").fullName("Staff").email("staff@roms.test")
                .passwordHash("encoded").role(UserRole.SERVICE_STAFF).isActive(true).build();
        table = RestaurantTable.builder().id(3L).tableCode("T03").isActive(true).build();
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    void authenticate(Long id) {
        var principal = UserPrincipal.builder().id(id).role(UserRole.OWNER).sessionId(10L).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    void saveUsers() { when(users.save(any())).thenAnswer(i -> i.getArgument(0)); }

    @Test @DisplayName("Users: create staff with trimmed identity normalized email and encoded password")
    void createStaff() {
        saveUsers(); when(encoder.encode("Secret123")).thenReturn("hashed");
        var request = new CreateUserRequest(" staff ","Secret123"," Nhân viên "," STAFF@ROMS.TEST ",UserRole.SERVICE_STAFF,null);
        var result = service.createUser(request);
        assertThat(result.getUsername()).isEqualTo("staff");
        assertThat(result.getFullName()).isEqualTo("Nhân viên");
        assertThat(result.getEmail()).isEqualTo("staff@roms.test");
        var saved = ArgumentCaptor.forClass(User.class); verify(users).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getTable()).isNull();
    }
    @Test @DisplayName("Users: duplicate username must not persist or encode passwords")
    void duplicateUsername() {
        when(users.existsByUsername("staff")).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("staff","Secret123","Staff",null,UserRole.OWNER,null))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any()); verifyNoInteractions(encoder);
    }
    @Test @DisplayName("Users: duplicate normalized email must not persist")
    void duplicateEmail() {
        when(users.existsByEmail("staff@roms.test")).thenReturn(true);
        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("new","Secret123","Staff"," STAFF@ROMS.TEST ",UserRole.OWNER,null))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any());
    }
    @Test @DisplayName("Users: tablet creation requires an assigned table")
    void tabletWithoutTable() {
        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("tablet","Secret123","Bàn",null,UserRole.TABLE_ACCOUNT,null))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any());
    }
    @Test @DisplayName("Users: tablet cannot be assigned to an inactive table")
    void tabletInactiveTable() {
        table.setIsActive(false); when(tables.findById(3L)).thenReturn(Optional.of(table));
        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("tablet","Secret123","Bàn",null,UserRole.TABLE_ACCOUNT,3L))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any());
    }
    @Test @DisplayName("Users: tablet cannot reuse a table assigned to another account")
    void tabletAssignedTable() {
        when(tables.findById(3L)).thenReturn(Optional.of(table));
        when(users.findByTableId(3L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("tablet","Secret123","Bàn",null,UserRole.TABLE_ACCOUNT,3L))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any());
    }
    @Test @DisplayName("Users: tablet creation links an available table and allows absent email")
    void createTablet() {
        saveUsers(); when(tables.findById(3L)).thenReturn(Optional.of(table));
        var result = service.createUser(new CreateUserRequest("tablet","Secret123","Bàn",null,UserRole.TABLE_ACCOUNT,3L));
        assertThat(result.getTableId()).isEqualTo(3L);
        assertThat(result.getRole()).isEqualTo(UserRole.TABLE_ACCOUNT);
        assertThat(result.getEmail()).isNull();
    }
    @Test @DisplayName("Users: staff account cannot be linked to a table")
    void staffAssignedTable() {
        assertThatThrownBy(() -> service.createUser(new CreateUserRequest("staff","Secret123","Staff",null,UserRole.OWNER,3L))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any());
    }
    @Test @DisplayName("Users: list trims search and preserves role active and pagination filters")
    void listFilteredUsers() {
        var page = PageRequest.of(0,10);
        when(users.findWithFilters(UserRole.SERVICE_STAFF,false,"Staff",page)).thenReturn(new PageImpl<>(List.of(user),page,1));
        var result = service.getUsers(UserRole.SERVICE_STAFF,false," Staff ",page);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(users).findWithFilters(UserRole.SERVICE_STAFF,false,"Staff",page);
    }
    @Test @DisplayName("Users: missing account raises not-found")
    void unknownUser() {
        assertThatThrownBy(() -> service.getUserById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test @DisplayName("Users: administrative password reset encodes new password and revokes sessions")
    void updatePassword() {
        saveUsers(); when(users.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.encode("New123")).thenReturn("new-hash");
        service.updateUser(7L,new UpdateUserRequest("Updated","staff@roms.test",UserRole.SERVICE_STAFF,null," New123 "));
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getUsername()).isEqualTo("staff");
        verify(sessions).revokeAllUserSessions(7L);
    }
    @Test @DisplayName("Users: editing profile without password leaves credentials and sessions unchanged")
    void updateWithoutPassword() {
        saveUsers(); when(users.findById(7L)).thenReturn(Optional.of(user));
        service.updateUser(7L,new UpdateUserRequest(" Updated ","staff@roms.test",UserRole.KITCHEN_STAFF,null));
        assertThat(user.getFullName()).isEqualTo("Updated");
        assertThat(user.getPasswordHash()).isEqualTo("encoded");
        verifyNoInteractions(encoder,sessions);
    }
    @Test @DisplayName("Users: deactivation revokes every session of the target account")
    void deactivateUser() {
        saveUsers(); authenticate(1L); when(users.findById(7L)).thenReturn(Optional.of(user));
        service.updateUserStatus(7L,new UpdateUserStatusRequest(false));
        assertThat(user.getIsActive()).isFalse(); verify(sessions).revokeAllUserSessions(7L);
    }
    @Test @DisplayName("Users: owner cannot deactivate their own account")
    void cannotDeactivateSelf() {
        authenticate(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.updateUserStatus(7L,new UpdateUserStatusRequest(false))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any()); verifyNoInteractions(sessions);
    }
    @Test @DisplayName("Users: cannot deactivate tablet while its table has an active service session")
    void cannotDeactivateOccupiedTablet() {
        user.setRole(UserRole.TABLE_ACCOUNT); user.setTable(table);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(tableSessions.existsByTableIdAndStatus(3L,ServiceSessionStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> service.updateUserStatus(7L,new UpdateUserStatusRequest(false))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any()); verifyNoInteractions(sessions);
    }
    @Test @DisplayName("Users: activating an account does not revoke sessions")
    void activateUser() {
        saveUsers(); user.setIsActive(false); when(users.findById(7L)).thenReturn(Optional.of(user));
        service.updateUserStatus(7L,new UpdateUserStatusRequest(true));
        assertThat(user.getIsActive()).isTrue(); verifyNoInteractions(sessions);
    }
    @Test @DisplayName("Users: unauthenticated profile and password operations are rejected")
    void unauthenticatedProfile() {
        assertThatThrownBy(() -> service.getCurrentUserProfile()).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.updateCurrentUserProfile(new UpdateProfileRequest("Name","a@roms.test"))).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("old","new"))).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(users,encoder,sessions);
    }
    @Test @DisplayName("Users: personal profile normalizes email and cannot change role")
    void editPersonalProfile() {
        saveUsers(); authenticate(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        var result = service.updateCurrentUserProfile(new UpdateProfileRequest(" Name "," NEW@ROMS.TEST "));
        assertThat(result.getFullName()).isEqualTo("Name");
        assertThat(result.getEmail()).isEqualTo("new@roms.test");
        assertThat(result.getRole()).isEqualTo(UserRole.SERVICE_STAFF);
    }
    @Test @DisplayName("Users: incorrect current password prevents persistence and revocation")
    void wrongCurrentPassword() {
        authenticate(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("wrong","New123"))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any()); verifyNoInteractions(sessions);
    }
    @Test @DisplayName("Users: password reuse is rejected")
    void samePassword() {
        authenticate(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("Old123","encoded")).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("Old123","Old123"))).isInstanceOf(BusinessRuleException.class);
        verify(users,never()).save(any()); verifyNoInteractions(sessions);
    }
    @Test @DisplayName("Users: valid password change persists hash before revoking all sessions")
    void changePassword() {
        saveUsers(); authenticate(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("Old123","encoded")).thenReturn(true);
        when(encoder.encode("New123")).thenReturn("new-hash");
        service.changePassword(new ChangePasswordRequest("Old123","New123"));
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        var order = inOrder(users,sessions); order.verify(users).save(user); order.verify(sessions).revokeAllUserSessions(7L);
    }
}
