package com.example.ootd.config;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

@TestInstance(Lifecycle.PER_CLASS)
public class RedisTestContainerConfig {

  static final GenericContainer<?> redisContainer;
  static {
    redisContainer = new GenericContainer<>("redis:7.2-alpine")
        .withExposedPorts(6379);
    redisContainer.start();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redisContainer::getHost);
    registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }
}
