package com.fastfood.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(properties = {"eureka.client.enabled=false"})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
