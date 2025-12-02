package io.javi.dapr.example.workflows;

import io.dapr.workflows.WorkflowActivity;
import io.dapr.workflows.WorkflowActivityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Activity3 implements WorkflowActivity {

  private final Logger logger = LoggerFactory.getLogger(Activity3.class);

  @Override
  public Object run(WorkflowActivityContext workflowActivityContext) {
    logger.info("Activity 3 called");
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    logger.info("Activity 3 finished");
    return null;
  }
}
