package io.javi.dapr.example.workflows;


import io.dapr.workflows.Workflow;
import io.dapr.workflows.WorkflowStub;
import org.springframework.stereotype.Component;

@Component
public class SimpleWorkflow implements Workflow {
  @Override
  public WorkflowStub create() {
    return ctx -> {

      ctx.getLogger().info("Simple workflow started.");

      ctx.callActivity(Activity1.class.getName()).await();
      ctx.callActivity(Activity2.class.getName()).await();
      ctx.waitForExternalEvent("hello").await();
      ctx.callActivity(Activity3.class.getName()).await();

      ctx.getLogger().info("Simple workflow completed.");
      ctx.complete(null);
    };
  }
}
