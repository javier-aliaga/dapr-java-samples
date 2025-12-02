package io.javi.dapr.example;

import io.dapr.spring.workflows.config.EnableDaprWorkflows;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDaprWorkflows
public class ExamplesApplication {

  public static void main(String[] args) {
    String newArgs = String.format("--grpc.server.port=%d", 3000);

    SpringApplication.run(ExamplesApplication.class, newArgs);
  }

}
