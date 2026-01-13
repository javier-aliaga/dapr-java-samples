package io.javi.dapr.example.workflows;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Activity1 implements WorkflowActivity {

  private final Logger logger = LoggerFactory.getLogger(Activity1.class);

  @Override
  public Object run(WorkflowActivityContext workflowActivityContext) {
    logger.info("Activity 1 called");
    logger.info("Traceparent: {}", workflowActivityContext.getTraceParent());

    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    logger.info("Activity 1 finished");
    return null;
  }
}
