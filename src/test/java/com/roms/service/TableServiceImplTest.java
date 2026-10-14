package com.roms.service;

import com.roms.common.PageResponse;
import com.roms.dto.request.CreateTableRequest;
import com.roms.dto.request.UpdateTableRequest;
import com.roms.dto.request.UpdateTableStatusRequest;
import com.roms.dto.response.TableResponse;
import com.roms.entity.RestaurantTable;
import com.roms.enums.ServiceSessionStatus;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.mapper.TableMapper;
import com.roms.repository.RestaurantTableRepository;
import com.roms.repository.ServiceSessionRepository;
import com.roms.service.impl.TableServiceImpl;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableServiceImplTest {

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private ServiceSessionRepository sessionRepository;

    @Mock
    private TableMapper tableMapper;

    @InjectMocks
    private TableServiceImpl tableService;

    private RestaurantTable sampleTable;
    private TableResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleTable = RestaurantTable.builder()
                .id(1L)
                .tableCode("T01")
                .capacity(4)
                .area("Main Hall")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleResponse = TableResponse.builder()
                .id(1L)
                .tableCode("T01")
                .capacity(4)
                .area("Main Hall")
                .isActive(true)
                .realtimeStatus("EMPTY")
                .build();
    }

    @Nested
    @DisplayName("getTables")
    class GetTables {

        @Test
        @DisplayName("should return paginated list of tables")
        void shouldReturnPaginatedTables() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<RestaurantTable> page = new PageImpl<>(List.of(sampleTable), pageable, 1);

            when(tableRepository.findWithFilters(isNull(), isNull(), isNull(), eq(pageable)))
                    .thenReturn(page);
            when(sessionRepository.findByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(tableMapper.toResponseWithStatus(sampleTable, null))
                    .thenReturn(sampleResponse);

            PageResponse<TableResponse> result = tableService.getTables(null, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getTableCode()).isEqualTo("T01");
        }
    }

    @Nested
    @DisplayName("getTableById")
    class GetTableById {

        @Test
        @DisplayName("should return table when found")
        void shouldReturnTableWhenFound() {
            when(tableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
            when(sessionRepository.findByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(tableMapper.toResponseWithStatus(sampleTable, null))
                    .thenReturn(sampleResponse);

            TableResponse result = tableService.getTableById(1L);

            assertThat(result.getTableCode()).isEqualTo("T01");
        }

        @Test
        @DisplayName("should throw when table not found")
        void shouldThrowWhenNotFound() {
            when(tableRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tableService.getTableById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    @Nested
    @DisplayName("createTable")
    class CreateTable {

        @Test
        @DisplayName("should create table successfully")
        void shouldCreateTableSuccessfully() {
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableCode("T11")
                    .capacity(6)
                    .area("Terrace")
                    .build();

            when(tableRepository.existsByTableCode("T11")).thenReturn(false);
            when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(inv -> {
                RestaurantTable saved = inv.getArgument(0);
                saved.setId(11L);
                return saved;
            });
            when(tableMapper.toResponseWithStatus(any(RestaurantTable.class), isNull()))
                    .thenReturn(TableResponse.builder().id(11L).tableCode("T11").build());

            TableResponse result = tableService.createTable(request);

            assertThat(result.getTableCode()).isEqualTo("T11");
            verify(tableRepository).save(any(RestaurantTable.class));
        }

        @Test
        @DisplayName("should throw on duplicate table code")
        void shouldThrowOnDuplicateCode() {
            CreateTableRequest request = CreateTableRequest.builder()
                    .tableCode("T01")
                    .capacity(4)
                    .build();

            when(tableRepository.existsByTableCode("T01")).thenReturn(true);

            assertThatThrownBy(() -> tableService.createTable(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already in use");
        }
    }

    @Nested
    @DisplayName("updateTable")
    class UpdateTable {

        @Test
        @DisplayName("should update table successfully")
        void shouldUpdateTableSuccessfully() {
            UpdateTableRequest request = UpdateTableRequest.builder()
                    .tableCode("T01-NEW")
                    .capacity(8)
                    .area("VIP Room")
                    .build();

            when(tableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
            when(tableRepository.existsByTableCode("T01-NEW")).thenReturn(false);
            when(tableRepository.save(any(RestaurantTable.class))).thenReturn(sampleTable);
            when(sessionRepository.findByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(tableMapper.toResponseWithStatus(any(), any())).thenReturn(sampleResponse);

            TableResponse result = tableService.updateTable(1L, request);

            assertThat(result).isNotNull();
            verify(tableRepository).save(any(RestaurantTable.class));
        }

        @Test
        @DisplayName("should throw on duplicate table code on update")
        void shouldThrowOnDuplicateCodeOnUpdate() {
            UpdateTableRequest request = UpdateTableRequest.builder()
                    .tableCode("T02")
                    .capacity(4)
                    .build();

            when(tableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
            when(tableRepository.existsByTableCode("T02")).thenReturn(true);

            assertThatThrownBy(() -> tableService.updateTable(1L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already in use");
        }
    }

    @Nested
    @DisplayName("updateTableStatus")
    class UpdateTableStatus {

        @Test
        @DisplayName("should deactivate table without active session")
        void shouldDeactivateTableWithoutActiveSession() {
            UpdateTableStatusRequest request = UpdateTableStatusRequest.builder()
                    .isActive(false)
                    .build();

            when(tableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
            when(sessionRepository.existsByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE))
                    .thenReturn(false);
            when(tableRepository.save(any())).thenReturn(sampleTable);
            when(sessionRepository.findByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(tableMapper.toResponseWithStatus(any(), any())).thenReturn(sampleResponse);

            TableResponse result = tableService.updateTableStatus(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw when deactivating table with active session")
        void shouldThrowWhenDeactivatingWithActiveSession() {
            UpdateTableStatusRequest request = UpdateTableStatusRequest.builder()
                    .isActive(false)
                    .build();

            when(tableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
            when(sessionRepository.existsByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE))
                    .thenReturn(true);

            assertThatThrownBy(() -> tableService.updateTableStatus(1L, request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("active service session");
        }

        @Test
        @DisplayName("should activate table successfully")
        void shouldActivateTableSuccessfully() {
            sampleTable.setIsActive(false);
            UpdateTableStatusRequest request = UpdateTableStatusRequest.builder()
                    .isActive(true)
                    .build();

            when(tableRepository.findById(1L)).thenReturn(Optional.of(sampleTable));
            when(tableRepository.save(any())).thenReturn(sampleTable);
            when(sessionRepository.findByTableIdAndStatus(1L, ServiceSessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(tableMapper.toResponseWithStatus(any(), any())).thenReturn(sampleResponse);

            TableResponse result = tableService.updateTableStatus(1L, request);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("getDistinctAreas")
    class GetDistinctAreas {

        @Test
        @DisplayName("should return distinct areas")
        void shouldReturnDistinctAreas() {
            when(tableRepository.findDistinctAreas())
                    .thenReturn(List.of("Main Hall", "Terrace", "VIP Room"));

            List<String> areas = tableService.getDistinctAreas();

            assertThat(areas).hasSize(3);
            assertThat(areas).containsExactly("Main Hall", "Terrace", "VIP Room");
        }
    }
}
