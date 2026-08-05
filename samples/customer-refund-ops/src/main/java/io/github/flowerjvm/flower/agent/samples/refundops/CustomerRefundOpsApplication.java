package io.github.flowerjvm.flower.agent.samples.refundops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CustomerRefundOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerRefundOpsApplication.class, args);
    }
}
