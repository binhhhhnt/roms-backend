package com.roms.integration;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component @RequiredArgsConstructor
public class CloudinaryClientAdapterImpl implements CloudinaryClientAdapter {
    private final Cloudinary cloudinary;
    private void requireConfigured() {
        if (cloudinary.config.cloudName == null || cloudinary.config.cloudName.isBlank()
                || cloudinary.config.apiKey == null || cloudinary.config.apiKey.isBlank()
                || cloudinary.config.apiSecret == null || cloudinary.config.apiSecret.isBlank()) {
            throw new IllegalStateException("Cloudinary credentials are not configured");
        }
    }
    @Override
    public String upload(byte[] bytes, String publicId) {
        requireConfigured();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, Map.of("public_id", publicId,
                    "resource_type", "image", "overwrite", false, "allowed_formats", java.util.List.of("jpg", "png", "webp")));
            Object url = result.get("secure_url");
            if (!(url instanceof String value) || !value.startsWith("https://") || value.length() > 500) {
                throw new IllegalStateException("Cloudinary returned an invalid image URL");
            }
            return value;
        } catch (Exception ex) {
            throw new IllegalStateException("Image upload failed", ex);
        }
    }
    @Override
    public void delete(String publicId) {
        requireConfigured();
        try {
            Object result = cloudinary.uploader().destroy(publicId, Map.of("resource_type", "image", "invalidate", true)).get("result");
            if (!"ok".equals(result) && !"not found".equals(result)) {
                throw new IllegalStateException("Cloudinary did not confirm image removal");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Image removal failed", ex);
        }
    }
}
