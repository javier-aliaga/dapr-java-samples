package io.javi.dapr.example.controller;


import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import io.dapr.workflows.client.DaprWorkflowClient;
import io.javi.dapr.example.workflows.SimpleWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "app.publisher.enabled", havingValue = "true")
public class AppRestController {

  private final Logger logger = LoggerFactory.getLogger(AppRestController.class);
  @Autowired
  private DaprClient client;
  @Autowired
  private DaprPreviewClient previewClient;
  @Autowired
  private DaprWorkflowClient daprWorkflowClient;
  private String lastInstanceId = "";

  @PostMapping("/send/{message}")
  public String sendMessage(@PathVariable("message") String message, @RequestParam(required = false) boolean rawPayload) {
    logger.info("Sending message: {}", message);

    Map<String, String> metadata = new HashMap<>();
    var topicaName = "topic";
    if (rawPayload) {
      logger.info("Sending raw payload");
      metadata.put("rawPayload", "true");
      metadata.put("content-type", "application/json");
      topicaName = "raw";
    }

    client.publishEvent("pubsub", topicaName, message, metadata)
        .block();

    return "Message sent successfully";
  }


  @PostMapping("/bulk/{topic}/{message}")
  public String bulkMessage(@PathVariable("topic") String topic, @PathVariable("message") String message) {
    var m1 = message + "1";
    var m2 = message + "2";
    var m3 = message + "3";
    logger.info("Sending messages: {} {} {}", m1, m2, m3);

    previewClient.publishEvents("pubsub", topic, "", m1, m2, m3)
        .block();

    return "Message sent successfully";
  }

  @PostMapping("/workflow")
  public String startWorkflow() {
    String instanceId = daprWorkflowClient.scheduleNewWorkflow(SimpleWorkflow.class);
    logger.info("Workflow instance {} started", instanceId);
    this.lastInstanceId = instanceId;
    return "New Workflow Instance created " + instanceId;
  }

  @PostMapping("/workflow/event")
  public String raiseEvent() {
    if ("".equals(this.lastInstanceId)) {
      return "No workflow instance to raise event";
    }

    daprWorkflowClient.raiseEvent(this.lastInstanceId, "hello", "Hello World!");

    return "Event raised successfully for workflow instance " + this.lastInstanceId;
  }
}
