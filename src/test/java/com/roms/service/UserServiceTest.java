package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.ChangePasswordRequest;
import com.roms.dto.request.CreateUserRequest;
import com.roms.dto.request.UpdateUserRequest;
import com.roms.dto.request.UpdateUserStatusRequest;
import com.roms.dto.response.UserResponse;
import com.roms.entity.RestaurantTable;
import com.roms.entity.User;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.UserRepository;
import com.roms.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantTableRepository tableRepository;

    @Test
    @DisplayName("Should query users with pagination and filter by role")
    void shouldGetUsersWithFilter() {
        PageResponse<UserResponse> response = userService.getUsers(UserRole.SERVICE_STAFF, true, null, PageRequest.of(0, 10));

        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getContent()).allMatch(u -> u.getRole() == UserRole.SERVICE_STAFF);
    }

    @Test
    @DisplayName("Should create staff user successfully without tableId")
    void shouldCreateStaffUserSuccessfully() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("new_waiter")
                .password("Password@123")
                .fullName("Nhân viên mới")
                .email("waiter@roms.vn")
                .role(UserRole.SERVICE_STAFF)
                .tableId(null)
                .build();

        UserResponse response = userService.createUser(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getUsername()).isEqualTo("new_waiter");
        assertThat(response.getRole()).isEqualTo(UserRole.SERVICE_STAFF);
        assertThat(response.getTableId()).isNull();
    }

    @Test
    @DisplayName("Should create TABLE_ACCOUNT successfully with unused table")
    void shouldCreateTableAccountWithUnusedTable() {
        // Create an unassigned table
        RestaurantTable table = tableRepository.save(RestaurantTable.builder()
                .tableCode("T99")
                .capacity(4)
                .isActive(true)
                .build());

        CreateUserRequest request = CreateUserRequest.builder()
                .username("table_t99")
                .password("Password@123")
                .fullName("Tablet Bàn T99")
                .role(UserRole.TABLE_ACCOUNT)
                .tableId(table.getId())
                .build();

        UserResponse response = userService.createUser(request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getUsername()).isEqualTo("table_t99");
        assertThat(response.getTableId()).isEqualTo(table.getId());
        assertThat(response.getTableCode()).isEqualTo("T99");
    }

    @Test
    @DisplayName("Should reject TABLE_ACCOUNT creation without tableId")
    void shouldRejectTableAccountWithoutTable() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("table_invalid")
                .password("Password@123")
                .fullName("Tablet Invalid")
                .role(UserRole.TABLE_ACCOUNT)
                .tableId(null)
                .build();

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Table ID is required for TABLE_ACCOUNT");
    }

    @Test
    @DisplayName("Should reject staff account creation with tableId")
    void shouldRejectStaffWithTable() {
        RestaurantTable table = tableRepository.findByTableCode("T01").orElseThrow();

        CreateUserRequest request = CreateUserRequest.builder()
                .username("waiter_with_table")
                .password("Password@123")
                .fullName("Waiter Table")
                .role(UserRole.SERVICE_STAFF)
                .tableId(table.getId())
                .build();

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Staff accounts cannot be linked to a table");
    }

    @Test
    @DisplayName("Should reject duplicate username")
    void shouldRejectDuplicateUsername() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("owner")
                .password("Password@123")
                .fullName("Owner Duplicate")
                .role(UserRole.OWNER)
                .build();

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Username is already taken");
    }

    @Test
    @DisplayName("Should reject TABLE_ACCOUNT when table is already assigned")
    void shouldRejectWhenTableAlreadyAssigned() {
        RestaurantTable table1 = tableRepository.findByTableCode("T01").orElseThrow();

        CreateUserRequest request = CreateUserRequest.builder()
                .username("table_t01_duplicate")
                .password("Password@123")
                .fullName("Tablet Duplicate")
                .role(UserRole.TABLE_ACCOUNT)
                .tableId(table1.getId())
                .build();

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already linked to user");
    }

    @Test
    @DisplayName("Should update user profile and information")
    void shouldUpdateUser() {
        User staff = userRepository.findByUsername("service01").orElseThrow();

        UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                .fullName("Trần Thị Thu Ngân Updated")
                .email("service01_new@roms.vn")
                .role(UserRole.SERVICE_STAFF)
                .tableId(null)
                .build();

        UserResponse response = userService.updateUser(staff.getId(), updateRequest);

        assertThat(response.getFullName()).isEqualTo("Trần Thị Thu Ngân Updated");
        assertThat(response.getEmail()).isEqualTo("service01_new@roms.vn");
    }

    @Test
    @DisplayName("Should deactivate user successfully")
    void shouldDeactivateUser() {
        User staff = userRepository.findByUsername("service02").orElseThrow();

        UserResponse response = userService.updateUserStatus(staff.getId(), new UpdateUserStatusRequest(false));

        assertThat(response.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should change password successfully when authenticated")
    void shouldChangePassword() {
        User staff = userRepository.findByUsername("service01").orElseThrow();

        // Simulate security authentication context
        UserPrincipal principal = UserPrincipal.create(staff, 999L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("Password@123")
                .newPassword("NewPassword@456")
                .build();

        userService.changePassword(request);

        User reloaded = userRepository.findById(staff.getId()).orElseThrow();
        assertThat(reloaded.getPasswordHash()).isNotEqualTo(staff.getPasswordHash());
    }

    @Test
    @DisplayName("Should fail changing password when current password does not match")
    void shouldFailChangePasswordWithWrongOldPassword() {
        User staff = userRepository.findByUsername("service01").orElseThrow();

        UserPrincipal principal = UserPrincipal.create(staff, 999L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("WrongPassword@123")
                .newPassword("NewPassword@456")
                .build();

        assertThatThrownBy(() -> userService.changePassword(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Current password does not match");
    }
}
