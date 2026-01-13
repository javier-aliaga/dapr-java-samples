package io.javi.dapr.example.service.pubsub;

import com.google.protobuf.Empty;
import io.dapr.v1.AppCallbackGrpc;
import io.dapr.v1.DaprAppCallbackProtos;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.stub.StreamObserver;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.javi.dapr.example.interceptors.MetadataInterceptor.METADATA_KEY;

/**
 * Class that encapsulates all client-side logic for Grpc.
 */
public class SubscriberGrpcService extends AppCallbackGrpc.AppCallbackImplBase {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(SubscriberGrpcService.class);
  private final List<DaprAppCallbackProtos.TopicSubscription> topicSubscriptionList = new ArrayList<>();

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

  private static void logGrpcMetadata() {
    try {
      Context context = Context.current();
      Metadata metadata = METADATA_KEY.get(context);

      if (metadata != null) {
        logger.info("Metadata found in context");
        String apiToken = metadata.get(Metadata.Key.of("dapr-api-token", Metadata.ASCII_STRING_MARSHALLER));
        if (apiToken != null) {
          logger.info("API Token extracted: {}", apiToken);
        } else {
          logger.info("No 'dapr-api-token' found in metadata");
        }
        logger.info("All metadata:");
        for (String key : metadata.keys()) {
          if (key.equals("grpc-trace-bin")) {
            byte[] value = metadata.get(Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER));
            logger.info("key: {}:{}", key, BulkSubscriberGrpcService.buildTraceparentFromGrpcTraceBin(value));
            continue;
          }
          String value = metadata.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
          logger.info("key: {}:{}", key, value);
        }
      } else {
        logger.info("No metadata found in context");
      }
    } catch (Exception e) {
      logger.info(" Error extracting metadata: {}", e.getMessage());
    }
  }

  @Override
  public void onTopicEvent(DaprAppCallbackProtos.TopicEventRequest request,
                           StreamObserver<DaprAppCallbackProtos.TopicEventResponse> responseObserver) {

    try {
      logGrpcMetadata();
      String data = request.getData().toStringUtf8().replace("\"", "");
      logger.info("Subscriber got: {}", data);
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
