package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.event.SessionStatusChangedEvent;
import com.roms.dto.event.TableTransferEvent;
import com.roms.dto.request.TransferTableRequest;
import com.roms.dto.response.ServiceSessionResponse;
import com.roms.dto.response.TableTransferHistoryResponse;
import com.roms.entity.RestaurantTable;
import com.roms.entity.ServiceSession;
import com.roms.entity.TableTransferHistory;
import com.roms.entity.User;
import com.roms.enums.ServiceSessionStatus;
import com.roms.enums.UserRole;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.TableMapper;
import com.roms.repository.OrderRepository;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.repository.TableTransferHistoryRepository;
import com.roms.repository.UserRepository;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.ServiceSessionServiceImpl;
import com.roms.websocket.StompDestinations;
import com.roms.websocket.StompEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceSessionServiceImplTest {

    @Mock
    private ServiceSessionRepository sessionRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private TableTransferHistoryRepository transferHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StompEventPublisher stompEventPublisher;

    @Mock
    private TableMapper tableMapper;

    @InjectMocks
    private ServiceSessionServiceImpl sessionService;

    private User staffUser;
    private UserPrincipal staffPrincipal;
    private RestaurantTable table1;
    private RestaurantTable table2;
    private ServiceSession activeSession;
    private ServiceSessionResponse sessionResponse;

    @BeforeEach
    void setUp() {
        staffUser = User.builder()
                .id(10L)
                .username("staff1")
                .fullName("Staff User")
                .role(UserRole.SERVICE_STAFF)
                .isActive(true)
                .build();

        staffPrincipal = UserPrincipal.builder()
                .id(10L)
                .username("staff1")
                .password("encoded")
                .fullName("Staff User")
                .role(UserRole.SERVICE_STAFF)
                .tableId(null)
                .sessionId(1L)
                .active(true)
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(staffPrincipal, null, staffPrincipal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        table1 = RestaurantTable.builder()
                .id(1L)
                .tableCode("T01")
                .capacity(4)
                .area("Main Hall")
                .isActive(true)
                .build();

        table2 = RestaurantTable.builder()
                .id(2L)
                .tableCode("T02")
                .capacity(6)
                .area("VIP")
                .isActive(true)
                .build();

        activeSession = ServiceSession.builder()
                .id(100L)
                .table(table1)
                .status(ServiceSessionStatus.ACTIVE)
                .openedBy(staffUser)
                .openedAt(LocalDateTime.now())
                .build();

        sessionResponse = ServiceSessionResponse.builder()
                .id(100L)
                .tableId(1L)
                .tableCode("T01")
                .status(ServiceSessionStatus.ACTIVE)
                .openedById(10L)
                .openedByName("Staff User")
                .openedAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("openSession")
    class OpenSession {

        @Test
        @DisplayName("should open session successfully and publish STOMP event")
        void shouldOpenSessionSuccessfully() {
            when(tableRepository.findById(1L)).thenReturn(Optional.of(table1));
            when(sessionRepository.existsByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE)).thenReturn(false);
            when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));
            when(sessionRepository.save(any(ServiceSession.class))).thenReturn(activeSession);
            when(tableMapper.toSessionResponse(activeSession)).thenReturn(sessionResponse);

            ServiceSessionResponse result = sessionService.openSession(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo(ServiceSessionStatus.ACTIVE);

            // Verify STOMP event is published
            verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.tables()), any(SessionStatusChangedEvent.class));
        }

        @Test
        @DisplayName("should allow TABLE_ACCOUNT to open session on assigned table")
        void shouldAllowTableAccountOnOwnTable() {
            UserPrincipal tabletPrincipal = UserPrincipal.builder()
                    .id(20L)
                    .username("tablet_t01")
                    .password("encoded")
                    .fullName("Tablet T01")
                    .role(UserRole.TABLE_ACCOUNT)
                    .tableId(1L)
                    .sessionId(2L)
                    .active(true)
                    .build();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(tabletPrincipal, null, tabletPrincipal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            User tabletUser = User.builder().id(20L).username("tablet_t01").role(UserRole.TABLE_ACCOUNT).isActive(true).build();

            when(tableRepository.findById(1L)).thenReturn(Optional.of(table1));
            when(sessionRepository.existsByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE)).thenReturn(false);
            when(userRepository.findById(20L)).thenReturn(Optional.of(tabletUser));
            when(sessionRepository.save(any(ServiceSession.class))).thenReturn(activeSession);
            when(tableMapper.toSessionResponse(activeSession)).thenReturn(sessionResponse);

            ServiceSessionResponse result = sessionService.openSession(1L);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should reject TABLE_ACCOUNT opening session on another table")
        void shouldRejectTableAccountOnDifferentTable() {
            UserPrincipal tabletPrincipal = UserPrincipal.builder()
                    .id(20L)
                    .username("tablet_t01")
                    .password("encoded")
                    .fullName("Tablet T01")
                    .role(UserRole.TABLE_ACCOUNT)
                    .tableId(1L)
                    .sessionId(2L)
                    .active(true)
                    .build();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(tabletPrincipal, null, tabletPrincipal.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(auth);

            when(tableRepository.findById(2L)).thenReturn(Optional.of(table2));

            assertThatThrownBy(() -> sessionService.openSession(2L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("TABLE_ACCOUNT can only open sessions on their assigned table");
        }

        @Test
        @DisplayName("should reject when table is inactive")
        void shouldRejectWhenTableIsInactive() {
            table1.setIsActive(false);
            when(tableRepository.findById(1L)).thenReturn(Optional.of(table1));

            assertThatThrownBy(() -> sessionService.openSession(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot open session on inactive table");
        }

        @Test
        @DisplayName("should reject when table already has active session")
        void shouldRejectWhenTableAlreadyHasActiveSession() {
            when(tableRepository.findById(1L)).thenReturn(Optional.of(table1));
            when(sessionRepository.existsByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> sessionService.openSession(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already has an active service session");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.openSession(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("closeSession")
    class CloseSession {

        @Test
        @DisplayName("should close session successfully when all orders are completed or cancelled")
        void shouldCloseSessionSuccessfully() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), anyCollection())).thenReturn(false);
            when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));

            ServiceSession closedSession = ServiceSession.builder()
                    .id(100L)
                    .table(table1)
                    .status(ServiceSessionStatus.CLOSED)
                    .openedBy(staffUser)
                    .closedBy(staffUser)
                    .openedAt(activeSession.getOpenedAt())
                    .closedAt(LocalDateTime.now())
                    .build();

            when(sessionRepository.save(any(ServiceSession.class))).thenReturn(closedSession);

            ServiceSessionResponse closedResponse = ServiceSessionResponse.builder()
                    .id(100L)
                    .status(ServiceSessionStatus.CLOSED)
                    .build();
            when(tableMapper.toSessionResponse(closedSession)).thenReturn(closedResponse);

            ServiceSessionResponse result = sessionService.closeSession(100L);

            assertThat(result.getStatus()).isEqualTo(ServiceSessionStatus.CLOSED);
            verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.tables()), any(SessionStatusChangedEvent.class));
        }

        @Test
        @DisplayName("should reject closing session when orders are still pending or cooking")
        void shouldRejectWhenOrdersArePendingOrCooking() {
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(orderRepository.existsByServiceSessionIdAndStatusIn(eq(100L), anyCollection())).thenReturn(true);

            assertThatThrownBy(() -> sessionService.closeSession(100L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot close session while orders are still pending or cooking");
        }

        @Test
        @DisplayName("should reject closing already closed session")
        void shouldRejectAlreadyClosedSession() {
            activeSession.setStatus(ServiceSessionStatus.CLOSED);
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));

            assertThatThrownBy(() -> sessionService.closeSession(100L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Service session is already closed");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when session not found")
        void shouldThrowWhenSessionNotFound() {
            when(sessionRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.closeSession(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("transferTable")
    class TransferTable {

        @Test
        @DisplayName("should transfer table successfully and publish TableTransferEvent")
        void shouldTransferTableSuccessfully() {
            TransferTableRequest request = TransferTableRequest.builder().toTableId(2L).build();

            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(tableRepository.findById(2L)).thenReturn(Optional.of(table2));
            when(sessionRepository.existsByTableIdAndStatus(2L, ServiceSessionStatus.ACTIVE)).thenReturn(false);
            when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));

            TableTransferHistory history = TableTransferHistory.builder()
                    .id(1L)
                    .serviceSession(activeSession)
                    .fromTable(table1)
                    .toTable(table2)
                    .transferredBy(staffUser)
                    .transferredAt(LocalDateTime.now())
                    .build();
            when(transferHistoryRepository.save(any(TableTransferHistory.class))).thenReturn(history);
            when(sessionRepository.save(any(ServiceSession.class))).thenReturn(activeSession);

            ServiceSessionResponse transferredResponse = ServiceSessionResponse.builder()
                    .id(100L)
                    .tableId(2L)
                    .tableCode("T02")
                    .status(ServiceSessionStatus.ACTIVE)
                    .build();
            when(tableMapper.toSessionResponse(activeSession)).thenReturn(transferredResponse);

            ServiceSessionResponse result = sessionService.transferTable(100L, request);

            assertThat(result.getTableId()).isEqualTo(2L);
            verify(transferHistoryRepository).save(any(TableTransferHistory.class));
            verify(stompEventPublisher).publishAfterCommit(eq(StompDestinations.tables()), any(TableTransferEvent.class));
        }

        @Test
        @DisplayName("should reject transferring to the same table")
        void shouldRejectTransferringToSameTable() {
            TransferTableRequest request = TransferTableRequest.builder().toTableId(1L).build();
            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));

            assertThatThrownBy(() -> sessionService.transferTable(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot transfer to the same table");
        }

        @Test
        @DisplayName("should reject transferring when target table is inactive")
        void shouldRejectWhenTargetTableInactive() {
            TransferTableRequest request = TransferTableRequest.builder().toTableId(2L).build();
            table2.setIsActive(false);

            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(tableRepository.findById(2L)).thenReturn(Optional.of(table2));

            assertThatThrownBy(() -> sessionService.transferTable(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cannot transfer to inactive table");
        }

        @Test
        @DisplayName("should reject transferring when target table already has active session")
        void shouldRejectWhenTargetTableOccupied() {
            TransferTableRequest request = TransferTableRequest.builder().toTableId(2L).build();

            when(sessionRepository.findByIdWithDetails(100L)).thenReturn(Optional.of(activeSession));
            when(tableRepository.findById(2L)).thenReturn(Optional.of(table2));
            when(sessionRepository.existsByTableIdAndStatus(2L, ServiceSessionStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> sessionService.transferTable(100L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already has an active service session");
        }
    }

    @Nested
    @DisplayName("getTransferHistory")
    class GetTransferHistory {

        @Test
        @DisplayName("should return paginated transfer history")
        void shouldReturnTransferHistory() {
            Pageable pageable = PageRequest.of(0, 10);
            TableTransferHistory history = TableTransferHistory.builder()
                    .id(1L)
                    .serviceSession(activeSession)
                    .fromTable(table1)
                    .toTable(table2)
                    .transferredBy(staffUser)
                    .transferredAt(LocalDateTime.now())
                    .build();

            Page<TableTransferHistory> page = new PageImpl<>(List.of(history), pageable, 1);
            TableTransferHistoryResponse historyResponse = TableTransferHistoryResponse.builder()
                    .id(1L)
                    .serviceSessionId(100L)
                    .fromTableId(1L)
                    .fromTableCode("T01")
                    .toTableId(2L)
                    .toTableCode("T02")
                    .transferredById(10L)
                    .transferredByName("Staff User")
                    .transferredAt(LocalDateTime.now())
                    .build();

            when(sessionRepository.existsById(100L)).thenReturn(true);
            when(transferHistoryRepository.findByServiceSessionId(100L, pageable)).thenReturn(page);
            when(tableMapper.toTransferHistoryResponse(history)).thenReturn(historyResponse);

            PageResponse<TableTransferHistoryResponse> result = sessionService.getTransferHistory(100L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getFromTableCode()).isEqualTo("T01");
            assertThat(result.getContent().get(0).getToTableCode()).isEqualTo("T02");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when session does not exist")
        void shouldThrowWhenSessionNotFound() {
            Pageable pageable = PageRequest.of(0, 10);
            when(sessionRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> sessionService.getTransferHistory(999L, pageable))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
