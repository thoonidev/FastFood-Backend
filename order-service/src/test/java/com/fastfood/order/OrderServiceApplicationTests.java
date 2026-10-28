package com.fastfood.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.test.context.TestPropertySource(locations = "classpath:secrets-test.properties")
@SpringBootTest(
        properties = {
            "eureka.client.enabled=false",
            "spring.datasource.url=jdbc:mysql://localhost:3306/fastfood_order_test_db?createDatabaseIfNotExist=true"
        })
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {}
}
