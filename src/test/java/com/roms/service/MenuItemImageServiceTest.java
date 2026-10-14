package com.roms.service;

import com.roms.entity.*;
import com.roms.enums.UserRole;
import com.roms.exception.*;
import com.roms.integration.CloudinaryClientAdapter;
import com.roms.mapper.MenuItemImageMapper;
import com.roms.repository.*;
import com.roms.security.UserPrincipal;
import com.roms.service.impl.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuItemImageServiceTest {
    private MenuItemRepository menus;
    private MenuItemImageRepository images;
    private CloudinaryClientAdapter client;
    private MenuContextCacheService cache;
    private MenuItemImageService service;
    private MenuItem menu;
    private MockMultipartFile file;

    @BeforeEach void setUp() {
        menus = mock(MenuItemRepository.class); images = mock(MenuItemImageRepository.class);
        client = mock(CloudinaryClientAdapter.class); cache = mock(MenuContextCacheService.class);
        service = new MenuItemImageServiceImpl(menus, images, client, new MenuItemImageMapper(), new MenuContextInvalidator(cache));
        menu = MenuItem.builder().id(8L).build();
        file = new MockMultipartFile("file", "food.png", "image/png", new byte[]{(byte)137,80,78,71,13,10,26,10,0});
        TransactionSynchronizationManager.initSynchronization();
        authenticate(UserRole.OWNER);
    }
    @AfterEach void tearDown() {
        TransactionSynchronizationManager.clearSynchronization(); SecurityContextHolder.clearContext();
    }
    private void authenticate(UserRole role) {
        var user = UserPrincipal.builder().id(1L).username("user").role(role).active(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
    private void prepareUpload() {
        when(menus.findByIdForUpdate(8L)).thenReturn(Optional.of(menu));
        when(images.saveAndFlush(any())).thenAnswer(call -> { MenuItemImage image = call.getArgument(0); image.setId(12L); return image; });
        when(client.upload(any(), eq("roms/menu-items/8/images/12"))).thenReturn("https://res.cloudinary.com/test/image/upload/food.png");
    }
    @Test void uploadMapsMetadataAndDoesNotDeleteCommittedAsset() {
        prepareUpload();
        var response = service.uploadImage(8L, file, 2);
        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getDisplayOrder()).isEqualTo(2);
        assertThat(response.getMenuItemId()).isEqualTo(8L);
        assertThat(response.getImageUrl()).startsWith("https://");
        verifyNoInteractions(cache);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> { s.afterCommit(); s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED); });
        verify(cache).invalidateMenuContext(); verify(client, never()).delete(any());
    }
    @Test void uploadFailureAndRollbackCleanUpDeterministicAsset() {
        prepareUpload();
        when(client.upload(any(), any())).thenThrow(new IllegalStateException("provider failed"));
        assertThatThrownBy(() -> service.uploadImage(8L, file, 0)).isInstanceOf(IllegalStateException.class);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(client).delete("roms/menu-items/8/images/12"); verifyNoInteractions(cache);
    }
    @Test void databaseFailureAfterUploadAlsoCleansAssetOnRollback() {
        prepareUpload();
        doAnswer(call -> { MenuItemImage image = call.getArgument(0); image.setId(12L); return image; })
                .doThrow(new IllegalStateException("database failed")).when(images).saveAndFlush(any());
        assertThatThrownBy(() -> service.uploadImage(8L, file, 0)).isInstanceOf(IllegalStateException.class);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(client).delete("roms/menu-items/8/images/12"); verifyNoInteractions(cache);
    }
    @Test void cleanupFailureDoesNotMaskOriginalRollback() {
        prepareUpload(); service.uploadImage(8L, file, 0);
        doThrow(new IllegalStateException("cleanup outage")).when(client).delete(any());
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK))).doesNotThrowAnyException();
        verifyNoInteractions(cache);
    }
    @Test void rejectsEmptyOversizedAndSpoofedFilesBeforeDatabaseOrProvider() throws Exception {
        var invalid = new MockMultipartFile[]{new MockMultipartFile("file", new byte[0]),
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]),
                new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", file.getBytes())};
        for (var candidate : invalid) assertThatThrownBy(() -> service.uploadImage(8L, candidate, 0)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(menus, images, client);
    }
    @Test void deletingForeignImageNeverCallsProvider() {
        when(menus.findByIdForUpdate(8L)).thenReturn(Optional.of(menu));
        when(images.findByIdAndMenuItemId(12L, 8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteImage(8L, 12L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(client); verify(images, never()).delete(any());
    }
    @Test void providerDeleteFailureKeepsMetadataForRetry() {
        when(menus.findByIdForUpdate(8L)).thenReturn(Optional.of(menu));
        when(images.findByIdAndMenuItemId(12L, 8L)).thenReturn(Optional.of(MenuItemImage.builder().id(12L).menuItem(menu).build()));
        doThrow(new IllegalStateException("provider outage")).when(client).delete(any());
        assertThatThrownBy(() -> service.deleteImage(8L, 12L)).isInstanceOf(IllegalStateException.class);
        verify(images, never()).delete(any()); verifyNoInteractions(cache);
    }
    @Test void deletesProviderBeforeMetadata() {
        when(menus.findByIdForUpdate(8L)).thenReturn(Optional.of(menu));
        var image = MenuItemImage.builder().id(12L).menuItem(menu).build();
        when(images.findByIdAndMenuItemId(12L, 8L)).thenReturn(Optional.of(image));
        service.deleteImage(8L, 12L);
        var ordered = inOrder(client, images);
        ordered.verify(client).delete("roms/menu-items/8/images/12"); ordered.verify(images).delete(image); ordered.verify(images).flush();
    }
    @Test void nonOwnerCannotUploadOrDelete() {
        authenticate(UserRole.TABLE_ACCOUNT);
        assertThatThrownBy(() -> service.uploadImage(8L, file, 0)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.deleteImage(8L, 12L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(menus, images, client);
    }
}
