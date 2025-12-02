package io.javi.dapr.example.service.pubsub;

import com.google.protobuf.Empty;
import io.dapr.v1.AppCallbackGrpc;
import io.dapr.v1.DaprAppCallbackProtos;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that encapsulates all client-side logic for Grpc.
 */
public class SubscriberGrpcService extends AppCallbackGrpc.AppCallbackImplBase {
  private final List<DaprAppCallbackProtos.TopicSubscription> topicSubscriptionList = new ArrayList<>();

  public static final Context.Key<Metadata> METADATA_KEY = Context.key("grpc-metadata");

  // gRPC interceptor to capture metadata
  public static class MetadataInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
      Context contextWithMetadata = Context.current().withValue(METADATA_KEY, headers);
      return Contexts.interceptCall(contextWithMetadata, call, headers, next);
    }
  }

  @Override
  public void listTopicSubscriptions(Empty request,
                                     StreamObserver<DaprAppCallbackProtos.ListTopicSubscriptionsResponse> responseObserver) {
    registerConsumer("pubsub", "topic", false, false);
    registerConsumer("pubsub", "bulk", true, false);
    registerConsumer("pubsub", "raw", false, true);

    try {
      DaprAppCallbackProtos.ListTopicSubscriptionsResponse.Builder builder = DaprAppCallbackProtos
          .ListTopicSubscriptionsResponse.newBuilder();
      topicSubscriptionList.forEach(builder::addSubscriptions);
      DaprAppCallbackProtos.ListTopicSubscriptionsResponse response = builder.build();
      responseObserver.onNext(response);
    } catch (Throwable e) {
      responseObserver.onError(e);
    } finally {
      responseObserver.onCompleted();
    }
  }

  @Override
  public void onTopicEvent(DaprAppCallbackProtos.TopicEventRequest request,
                           StreamObserver<DaprAppCallbackProtos.TopicEventResponse> responseObserver) {

    try {
      try {
        Context context = Context.current();
        Metadata metadata = METADATA_KEY.get(context);

        if (metadata != null) {
          System.out.println("Metadata found in context");
          String apiToken = metadata.get(Metadata.Key.of("dapr-api-token", Metadata.ASCII_STRING_MARSHALLER));
          if (apiToken != null) {
            System.out.println("API Token extracted: " + apiToken);
          } else {
            System.out.println("No 'dapr-api-token' found in metadata");
          }
          System.out.println("All metadata:");
          for (String key : metadata.keys()) {
            if (key.equals("grpc-trace-bin")) {
              byte[] value = metadata.get(Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER));
              System.out.println("key: " + key + ": " + BulkSubscriberGrpcService.buildTraceparentFromGrpcTraceBin(value));
              continue;
            }
            String value = metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
            System.out.println("key: " + key + ": " + value);
          }
        } else {
          System.out.println("No metadata found in context");
        }
      } catch (Exception e) {
        System.out.println(" Error extracting metadata: " + e.getMessage());
      }

      String data = request.getData().toStringUtf8().replace("\"", "");
      System.out.println("Subscriber got: " + data);
      DaprAppCallbackProtos.TopicEventResponse response = DaprAppCallbackProtos.TopicEventResponse.newBuilder()
          .setStatus(DaprAppCallbackProtos.TopicEventResponse.TopicEventResponseStatus.SUCCESS)
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Throwable e) {
      responseObserver.onError(e);
    }
  }

  /**
   * Add pubsub name and topic to topicSubscriptionList.
   *
   * @param topic the topic
   * @param pubsubName the pubsub name
   * @param isRawPayload flag to enable/disable raw payload mode
   */
  public void registerConsumer(String pubsubName, String topic, boolean isBulkMessage, boolean isRawPayload) {
    DaprAppCallbackProtos.TopicSubscription.Builder builder = DaprAppCallbackProtos.TopicSubscription
        .newBuilder()
        .setPubsubName(pubsubName)
        .setTopic(topic)
        .setBulkSubscribe(DaprAppCallbackProtos.BulkSubscribeConfig.newBuilder().setEnabled(isBulkMessage));

    if (isRawPayload) {
      builder.putMetadata("rawPayload", "true");
    }

    topicSubscriptionList.add(builder.build());
  }

}
