package com.roms.integration;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudinaryClientAdapterImplTest {
    private Cloudinary configured() {
        return spy(new Cloudinary(Map.of("cloud_name", "test", "api_key", "key", "api_secret", "secret")));
    }
    @Test void uploadUsesFixedIdNoOverwriteAndSecureUrl() throws Exception {
        var cloud = configured(); var uploader = mock(Uploader.class); when(cloud.uploader()).thenReturn(uploader);
        byte[] bytes = {1};
        when(uploader.upload(eq(bytes), anyMap())).thenReturn(Map.of("secure_url", "https://example.com/image.png"));
        assertThat(new CloudinaryClientAdapterImpl(cloud).upload(bytes, "roms/menu-items/8/images/12")).startsWith("https://");
        verify(uploader).upload(eq(bytes), argThat(options -> "roms/menu-items/8/images/12".equals(options.get("public_id"))
                && Boolean.FALSE.equals(options.get("overwrite")) && "image".equals(options.get("resource_type"))));
    }
    @Test void notFoundDeleteIsSuccessfulForRetryAfterDatabaseFailure() throws Exception {
        var cloud = configured(); var uploader = mock(Uploader.class); when(cloud.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "not found"));
        assertThatCode(() -> new CloudinaryClientAdapterImpl(cloud).delete("asset")).doesNotThrowAnyException();
    }
    @Test void unconfirmedDeleteAndInvalidUrlAreRejected() throws Exception {
        var cloud = configured(); var uploader = mock(Uploader.class); when(cloud.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "error"));
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "http://example.com/image"));
        var adapter = new CloudinaryClientAdapterImpl(cloud);
        assertThatThrownBy(() -> adapter.delete("asset")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adapter.upload(new byte[]{1}, "asset")).isInstanceOf(IllegalStateException.class);
    }
    @Test void missingCredentialsFailWithoutNetworkRequest() {
        var cloud = new Cloudinary(Map.of("cloud_name", "", "api_key", "", "api_secret", ""));
        assertThatThrownBy(() -> new CloudinaryClientAdapterImpl(cloud).upload(new byte[]{1}, "asset"))
                .isInstanceOf(IllegalStateException.class).hasMessage("Cloudinary credentials are not configured");
    }
}
