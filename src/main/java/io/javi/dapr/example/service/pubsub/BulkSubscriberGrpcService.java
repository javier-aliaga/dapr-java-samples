package io.javi.dapr.example.service.pubsub;

import java.util.function.Consumer;

import io.dapr.v1.AppCallbackAlphaGrpc;
import io.dapr.v1.DaprAppCallbackProtos.TopicEventBulkRequestEntry;
import io.dapr.v1.DaprAppCallbackProtos.TopicEventBulkResponse;
import io.dapr.v1.DaprAppCallbackProtos.TopicEventBulkResponseEntry;
import io.dapr.v1.DaprAppCallbackProtos.TopicEventResponse.TopicEventResponseStatus;
import io.grpc.Context;
import io.grpc.Metadata;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapGetter;

/**
 * Class that encapsulates all client-side logic for Grpc.
 */
public class BulkSubscriberGrpcService extends AppCallbackAlphaGrpc.AppCallbackAlphaImplBase {

  private static final Tracer tracer =  GlobalOpenTelemetry.getTracer("dapr-subscriber");

  private static final TextMapGetter<TopicEventBulkRequestEntry> ENTRY_GETTER =
  new TextMapGetter<>() {
    @Override public Iterable<String> keys(TopicEventBulkRequestEntry e) {
      return java.util.Arrays.asList("traceparent", "tracestate");
    }
    @Override public String get(TopicEventBulkRequestEntry e, String key) {
      String v = e.getMetadataMap().get(key);
      if (v != null) return v;
      var ext = e.getCloudEvent().getExtensions().getFieldsOrDefault(key, null);
      if (ext != null && ext.hasStringValue()) return ext.getStringValue();
      return null;
    }
  };

  @Override
  public void onBulkTopicEventAlpha1(io.dapr.v1.DaprAppCallbackProtos.TopicEventBulkRequest request,
      io.grpc.stub.StreamObserver<io.dapr.v1.DaprAppCallbackProtos.TopicEventBulkResponse> responseObserver) {

    extractMetadata();

    try {

      TopicEventBulkResponse.Builder responseBuilder = TopicEventBulkResponse.newBuilder();

      if (request.getEntriesCount() == 0) {
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
      }

      System.out.println("Bulk Subscriber received " + request.getEntriesCount() + " messages.");

      for (TopicEventBulkRequestEntry entry : request.getEntriesList()) {
        instrumentEvent(request.getTopic(),entry,(TopicEventBulkRequestEntry e)->processEvent(responseBuilder, e));
      }
      TopicEventBulkResponse response = responseBuilder.build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Throwable e) {
      responseObserver.onError(e);
    }
  }


  private void processEvent(TopicEventBulkResponse.Builder responseBuilder, TopicEventBulkRequestEntry entry) {
    try {
      System.out.printf("Bulk Subscriber message has entry ID: %s\n", entry.getEntryId());
      System.out.printf("Bulk Subscriber got: %s\n", entry.getCloudEvent().getData().toStringUtf8());

      entry.getCloudEvent().getExtensions().getFieldsMap().forEach((key, value) -> {
        System.out.println("Extension key: " + key + ", value: " + value);
      });

      entry.getMetadataMap().forEach((key, value) -> {
        System.out.println("Metadata key: " + key + ", value: " + value);
      });

      TopicEventBulkResponseEntry.Builder responseEntryBuilder = TopicEventBulkResponseEntry
          .newBuilder()
          .setEntryId(entry.getEntryId())
          .setStatusValue(TopicEventResponseStatus.SUCCESS_VALUE);
      responseBuilder.addStatuses(responseEntryBuilder);
    } catch (Throwable e) {
      TopicEventBulkResponseEntry.Builder responseEntryBuilder = TopicEventBulkResponseEntry
          .newBuilder()
          .setEntryId(entry.getEntryId())
          .setStatusValue(TopicEventResponseStatus.RETRY_VALUE);
      responseBuilder.addStatuses(responseEntryBuilder);
    }
  }

  public void instrumentEvent(String topic, TopicEventBulkRequestEntry entry, Consumer<TopicEventBulkRequestEntry> consumer) {

    io.opentelemetry.context.Context parent =
          GlobalOpenTelemetry.getPropagators()
              .getTextMapPropagator()
              .extract(io.opentelemetry.context.Context.root(), entry, ENTRY_GETTER);

      Span span =
          tracer.spanBuilder("pubsub " + topic)
              .setParent(parent)
              .setSpanKind(SpanKind.CONSUMER)
              // optional attributes that help in Jaeger/search
              .setAttribute("messaging.system", "pubsub")
              .setAttribute("messaging.destination", "topic")
              .startSpan();

      try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
       consumer.accept(entry);
      } catch (Throwable t) {
        span.recordException(t);
        span.setStatus(StatusCode.ERROR);
        throw t;
      } finally {
        span.end();
      }
  }

  public void extractMetadata() {
    try {
      Context context = Context.current();
      Metadata metadata = SubscriberGrpcService.METADATA_KEY.get(context);

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
            System.out.println("key: " + key + ": " + buildTraceparentFromGrpcTraceBin(value));
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
  }

  public static String buildTraceparentFromGrpcTraceBin(byte[] bytes) {
    if (bytes == null || bytes.length < 26)
      return null; // 1 (version) + 16 (traceId) + 8 (spanId) + 1 (flags)

    if (bytes != null && bytes.length >= 29 && bytes[0] == 0 && bytes[1] == 0 && bytes[18] == 1 && bytes[27] == 2) {
      byte[] traceId = java.util.Arrays.copyOfRange(bytes, 2, 18);
      byte[] spanId = java.util.Arrays.copyOfRange(bytes, 19, 27);
      int flags = bytes[28] & 0xff;
      return String.format("00-%s-%s-%02x", toHex(traceId), toHex(spanId), flags);
    }

    return null;
  }

  public static String toHex(byte[] data) {
    char[] out = new char[data.length * 2];
    final char[] hex = "0123456789abcdef".toCharArray();
    int j = 0;
    for (byte b : data) {
      int v = b & 0xff;
      out[j++] = hex[v >>> 4];
      out[j++] = hex[v & 0x0f];
    }
    return new String(out);
  }

}
