package com.fastfood.socket;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(properties = {"eureka.client.enabled=false", "fastfood.socket.port=0"})
class SocketServerApplicationTests {

	@Test
	void contextLoads() {
	}

}
