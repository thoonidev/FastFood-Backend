package com.fastfood.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(properties = {"eureka.client.enabled=false", "fastfood.demo.enabled=false",
        "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_auth_test_db?createDatabaseIfNotExist=true"})
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
