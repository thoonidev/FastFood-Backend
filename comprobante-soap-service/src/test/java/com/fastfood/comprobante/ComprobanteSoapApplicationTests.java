package com.fastfood.comprobante;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(properties = {"eureka.client.enabled=false",
        "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_comprobante_test_db?createDatabaseIfNotExist=true"})
class ComprobanteSoapApplicationTests {

	@Test
	void contextLoads() {
	}

}
