package io.javi.dapr.example.interceptors;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;


// gRPC interceptor to capture metadata and log it
public class MetadataInterceptor implements ServerInterceptor {
  public static final Context.Key<Metadata> METADATA_KEY = Context.key("grpc-metadata");
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MetadataInterceptor.class);

  @Value("${app.grpc.metadata.interceptor.enabled}")
  private static final boolean grpcMetadataInterceptorEnabled = false;

  public static ServerBuilder addMetadataIntercepor(ServerBuilder serverBuilder) {
    if (grpcMetadataInterceptorEnabled) {
      logger.info("Enabling Metadata Interceptor");
      serverBuilder.intercept(new MetadataInterceptor());
    }

    return serverBuilder;
  }

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    Context contextWithMetadata = Context.current().withValue(METADATA_KEY, headers);
    return Contexts.interceptCall(contextWithMetadata, call, headers, next);
  }
}