# PubSub Trace propagation reproducer for Pulsar

Create a cluster":

```sh
kind create cluster
```

Install Dapr: 
```sh
helm repo add dapr https://dapr.github.io/helm-charts/
helm repo update
helm upgrade --install dapr dapr/dapr \
--version=1.16.0 \
--namespace dapr-system \
--create-namespace \
--wait
```


Install Kafka (replace for pulsar)

```sh
helm install kafka oci://registry-1.docker.io/bitnamicharts/kafka --version 22.1.5 --set "provisioning.topics[0].name=events-topic" --set "provisioning.topics[0].partitions=1" --set "persistence.size=1Gi" --set "image.repository=bitnamilegacy/kafka"
```


## Installing Observability infrastructure

We will be using OpenTelemetry for collecting telemetry. This demo has support for a couple of different exports, e.g. jaeger tracing or dash0.

Let's start by installing Jaeger into our cluster:
```sh
helm repo add jaegertracing https://jaegertracing.github.io/helm-charts
helm repo update
helm install jaeger jaegertracing/jaeger  -f jaeger/values.yaml --version 3.4.1
```
Verify that Jaeger is running:
```sh
kubectl port-forward svc/jaeger-query 16686
```
Go to localhost:16686 and you should see Jaeger running.

Next, we create a new namespace for the opentelemetry services:

```sh
kubectl create namespace opentelemetry
```


Next, install the OpenTelemetry Collector:
```sh
helm install otel-collector open-telemetry/opentelemetry-collector \
    --namespace opentelemetry \
    -f collector/config.yaml
```

Once installed, install the OpenTelemetry Operator:
```sh
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm upgrade --install opentelemetry-operator open-telemetry/opentelemetry-operator --namespace opentelemetry
```

We can now start to configure, how our auto-instrumentation should work by applying the `Instrumentation` resource:
```sh
kubectl apply -f instrumentation/instrumentation.yaml
```

## Install redis

```sh 
helm install redis bitnami/redis --namespace dapr-tests --set global.redis.password=Daprio7p --set master.disableCommands=null
```

## Install the app

```sh
kubectl apply -f k8s/
```

Port Forward to send requests: 

```sh
kubectl port-forward svc/app-publisher-svc 8080:80
```

send the following request: 

```sh
curl -X POST localhost:8080/send/hello
```