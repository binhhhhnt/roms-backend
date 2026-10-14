package com.roms.integration;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

/** Explicit opt-in: creates and removes one uniquely named fixture, never menu/DB data. */
@EnabledIfSystemProperty(named = "roms.test.cloudinary", matches = "true")
class CloudinaryLiveSmokeTest {
    @Test void uploadAndRemoveIsolatedPngFixture() throws Exception {
        Properties properties = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (InputStream input = Files.newInputStream(Path.of(".env"))) { properties.load(input); }
        }
        var cloudinary = new Cloudinary(Map.of("cloud_name", setting(properties, "CLOUDINARY_CLOUD_NAME"),
                "api_key", setting(properties, "CLOUDINARY_API_KEY"), "api_secret", setting(properties, "CLOUDINARY_API_SECRET"), "secure", true));
        var client = new CloudinaryClientAdapterImpl(cloudinary);
        String publicId = "roms/be007-smoke/" + UUID.randomUUID();
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5L8AAAAASUVORK5CYII=");
        boolean uploaded = false;
        boolean deleted = false;
        try {
            try { client.upload(png, publicId); uploaded = true; }
            catch (Exception ignored) { /* Provider errors may contain secrets: do not print causes. */ }
        } finally {
            try { client.delete(publicId); deleted = true; }
            catch (Exception ignored) { /* Fail below with only the non-secret fixture identifier. */ }
        }
        if (!deleted) fail("Cloudinary fixture cleanup failed; check asset " + publicId);
        if (!uploaded) fail("Cloudinary upload failed; verify credentials/provider configuration (details suppressed)");
    }
    private String setting(Properties properties, String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = properties.getProperty(name, "");
        if (value.isBlank()) fail("Missing configuration: " + name);
        return value;
    }
}
