package com.webbank;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Full application on a random port against a real PostgreSQL (Testcontainers). Every
 * integration test uses the same configuration, so they share one Spring context and one
 * database container. Tests never delete data: each creates its own customers and accounts,
 * and since all money enters through the ledger the global invariants always hold.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, TestBank.class })
@ActiveProfiles("test")
public @interface IntegrationTest {

}
