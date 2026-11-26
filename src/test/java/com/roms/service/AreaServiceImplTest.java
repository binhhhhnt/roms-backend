package com.roms.service;

import com.roms.dto.request.CreateAreaRequest;
import com.roms.dto.request.UpdateAreaRequest;
import com.roms.dto.request.UpdateAreaStatusRequest;
import com.roms.dto.response.AreaResponse;
import com.roms.entity.Area;
import com.roms.exception.BusinessRuleException;
import com.roms.exception.ResourceNotFoundException;
import com.roms.repository.AreaRepository;
import com.roms.repository.RestaurantTableRepository;
import com.roms.service.impl.AreaServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AreaServiceImplTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @InjectMocks
    private AreaServiceImpl areaService;

    @Nested
    @DisplayName("getAreas")
    class GetAreas {

        @Test
        @DisplayName("should return areas with activeTableCount using single aggregate query")
        void shouldReturnAreasWithCounts() {
            Area a1 = Area.builder().id(1L).name("Tầng 1").isActive(true).build();
            Area a2 = Area.builder().id(2L).name("VIP").isActive(false).build();

            when(areaRepository.findAllByOrderByNameAsc()).thenReturn(List.of(a1, a2));
            when(tableRepository.countActiveTablesGroupByArea())
                    .thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));

            List<AreaResponse> result = areaService.getAreas(null);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Tầng 1");
            assertThat(result.get(0).getActiveTableCount()).isEqualTo(5L);
            assertThat(result.get(1).getActiveTableCount()).isEqualTo(0L);
            verify(tableRepository, times(1)).countActiveTablesGroupByArea();
        }
    }

    @Nested
    @DisplayName("createArea")
    class CreateArea {

        @Test
        @DisplayName("should create area successfully")
        void shouldCreateArea() {
            when(areaRepository.existsByNameIgnoreCase("Sân vườn")).thenReturn(false);
            when(areaRepository.saveAndFlush(any(Area.class))).thenAnswer(inv -> {
                Area saved = inv.getArgument(0);
                saved.setId(10L);
                return saved;
            });

            AreaResponse response = areaService.createArea(new CreateAreaRequest("  Sân vườn  "));

            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getName()).isEqualTo("Sân vườn");
            assertThat(response.getIsActive()).isTrue();
            assertThat(response.getActiveTableCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("should throw when duplicate name exists")
        void shouldThrowOnDuplicateName() {
            when(areaRepository.existsByNameIgnoreCase("Tầng 1")).thenReturn(true);

            assertThatThrownBy(() -> areaService.createArea(new CreateAreaRequest("Tầng 1")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should convert DataIntegrityViolationException to BusinessRuleException on race condition")
        void shouldHandleRaceConditionDuplicate() {
            when(areaRepository.existsByNameIgnoreCase("VIP")).thenReturn(false);
            when(areaRepository.saveAndFlush(any(Area.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> areaService.createArea(new CreateAreaRequest("VIP")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("updateArea")
    class UpdateArea {

        @Test
        @DisplayName("should rename area successfully")
        void shouldRenameArea() {
            Area area = Area.builder().id(1L).name("Tầng 1").isActive(true).build();
            when(areaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(area));
            when(areaRepository.existsByNameIgnoreCaseAndIdNot("Khu A", 1L)).thenReturn(false);
            when(areaRepository.saveAndFlush(any(Area.class))).thenReturn(area);
            when(tableRepository.countByAreaIdAndIsActiveTrue(1L)).thenReturn(3L);

            AreaResponse response = areaService.updateArea(1L, new UpdateAreaRequest("Khu A"));

            assertThat(response.getName()).isEqualTo("Khu A");
            assertThat(response.getActiveTableCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("should throw 404 when area not found")
        void shouldThrowWhenNotFound() {
            when(areaRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> areaService.updateArea(99L, new UpdateAreaRequest("Khu B")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateAreaStatus")
    class UpdateAreaStatus {

        @Test
        @DisplayName("should deactivate area when no active tables exist")
        void shouldDeactivateWhenNoActiveTables() {
            Area area = Area.builder().id(1L).name("Sân thượng").isActive(true).build();
            when(areaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(area));
            when(tableRepository.countByAreaIdAndIsActiveTrue(1L)).thenReturn(0L);
            when(areaRepository.save(any(Area.class))).thenReturn(area);

            AreaResponse response = areaService.updateAreaStatus(1L, new UpdateAreaStatusRequest(false));

            assertThat(response.getIsActive()).isFalse();
        }

        @Test
        @DisplayName("should block deactivation when active tables exist")
        void shouldBlockDeactivationWhenActiveTablesExist() {
            Area area = Area.builder().id(1L).name("Tầng 1").isActive(true).build();
            when(areaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(area));
            when(tableRepository.countByAreaIdAndIsActiveTrue(1L)).thenReturn(4L);

            assertThatThrownBy(() -> areaService.updateAreaStatus(1L, new UpdateAreaStatusRequest(false)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("active table");

            verify(areaRepository, never()).save(any());
        }
    }
}
