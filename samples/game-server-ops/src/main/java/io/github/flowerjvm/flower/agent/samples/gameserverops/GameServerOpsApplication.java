package io.github.flowerjvm.flower.agent.samples.gameserverops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GameServerOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameServerOpsApplication.class, args);
    }
}
