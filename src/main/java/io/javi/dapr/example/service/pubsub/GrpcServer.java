package io.javi.dapr.example.service.pubsub;

import io.dapr.v1.AppCallbackHealthCheckGrpc.AppCallbackHealthCheckImplBase;
import io.dapr.v1.DaprAppCallbackProtos;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


@Service
public class GrpcServer extends AppCallbackHealthCheckImplBase{
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    @Value("${app.consumer.enabled}")
    private boolean consumerEnabled = false;

    @PostConstruct
    public void start() {
        new Thread(() -> {
            ServerBuilder serverBuilder = ServerBuilder.forPort(3000)
                .intercept(new SubscriberGrpcService.MetadataInterceptor());
                if (consumerEnabled) {
                    serverBuilder.
                        addService(new SubscriberGrpcService()).
                        addService(new BulkSubscriberGrpcService()).
                        addService(this);
                }
                
                Server server = serverBuilder.build();
            try {
              server.start();
              logger.info("GRPC Server Started and listener registered.");
              server.awaitTermination();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
      
          }).start();
    }

    @Override
    public void healthCheck(com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<io.dapr.v1.DaprAppCallbackProtos.HealthCheckResponse> responseObserver) {
            responseObserver.onNext(DaprAppCallbackProtos.HealthCheckResponse.newBuilder().build());
            responseObserver.onCompleted();
    }
  
    
}
