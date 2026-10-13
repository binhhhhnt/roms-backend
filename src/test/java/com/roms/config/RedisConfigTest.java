package com.roms.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Test
    @DisplayName("RedisConfig creates StringRedisTemplate and RedisTemplate beans")
    void testBeanCreation() {
        RedisConfig config = new RedisConfig();

        StringRedisTemplate stringTemplate = config.stringRedisTemplate(connectionFactory);
        assertThat(stringTemplate).isNotNull();
        assertThat(stringTemplate.getConnectionFactory()).isEqualTo(connectionFactory);

        RedisTemplate<String, Object> objectTemplate = config.redisTemplate(connectionFactory);
        assertThat(objectTemplate).isNotNull();
        assertThat(objectTemplate.getConnectionFactory()).isEqualTo(connectionFactory);
        assertThat(objectTemplate.getKeySerializer()).isNotNull();
        assertThat(objectTemplate.getValueSerializer()).isNotNull();
    }

    @Test
    @DisplayName("redisConnectionFactory creates standalone connection when redisUrl is blank")
    void testConnectionFactory_default() {
        RedisConfig config = new RedisConfig();
        LettuceConnectionFactory factory = config.redisConnectionFactory("", "localhost", 6379, "");
        assertThat(factory).isNotNull();
        assertThat(factory.getHostName()).isEqualTo("localhost");
        assertThat(factory.getPort()).isEqualTo(6379);
    }

    @Test
    @DisplayName("redisConnectionFactory parses valid redis URL")
    void testConnectionFactory_withUrl() {
        RedisConfig config = new RedisConfig();
        LettuceConnectionFactory factory = config.redisConnectionFactory(
                "redis://default:mySecret123@redis.cloud.io:12345", "localhost", 6379, "");
        assertThat(factory).isNotNull();
        assertThat(factory.getHostName()).isEqualTo("redis.cloud.io");
        assertThat(factory.getPort()).isEqualTo(12345);
        assertThat(factory.getPassword()).isEqualTo("mySecret123");
    }

    @Test
    @DisplayName("redisConnectionFactory strips redis-cli command prefix automatically")
    void testConnectionFactory_withRedisCliPrefix() {
        RedisConfig config = new RedisConfig();
        LettuceConnectionFactory factory = config.redisConnectionFactory(
                "redis-cli -u redis://default:secretPass@muscle.db.redis.io:10579", "localhost", 6379, "");
        assertThat(factory).isNotNull();
        assertThat(factory.getHostName()).isEqualTo("muscle.db.redis.io");
        assertThat(factory.getPort()).isEqualTo(10579);
        assertThat(factory.getPassword()).isEqualTo("secretPass");
    }
}
