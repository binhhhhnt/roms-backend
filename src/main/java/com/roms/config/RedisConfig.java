package com.roms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.net.URI;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${REDIS_URL:${spring.data.redis.url:}}") String redisUrl,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword) {

        String cleanedUrl = cleanRedisUrl(redisUrl);
        if (StringUtils.hasText(cleanedUrl)) {
            URI uri = URI.create(cleanedUrl);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : 6379;
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);

            String userInfo = uri.getUserInfo();
            if (StringUtils.hasText(userInfo)) {
                String[] parts = userInfo.split(":", 2);
                if (parts.length == 2) {
                    if (StringUtils.hasText(parts[0])) {
                        config.setUsername(parts[0]);
                    }
                    config.setPassword(RedisPassword.of(parts[1]));
                } else {
                    config.setPassword(RedisPassword.of(parts[0]));
                }
            }

            LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();
            if ("rediss".equalsIgnoreCase(uri.getScheme())) {
                builder.useSsl();
            }
            return new LettuceConnectionFactory(config, builder.build());
        }

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (StringUtils.hasText(redisPassword)) {
            config.setPassword(RedisPassword.of(redisPassword));
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    private String cleanRedisUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("redis-cli -u ")) {
            trimmed = trimmed.substring("redis-cli -u ".length()).trim();
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }
}
