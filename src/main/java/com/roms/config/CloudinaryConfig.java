package com.roms.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary(@Value("${app.cloudinary.cloud-name:}") String cloud,
                                 @Value("${app.cloudinary.api-key:}") String key,
                                 @Value("${app.cloudinary.api-secret:}") String secret) {
        return new Cloudinary(Map.of("cloud_name", cloud, "api_key", key, "api_secret", secret, "secure", true));
    }
}
