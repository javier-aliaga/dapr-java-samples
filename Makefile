build:
	mkdir -p .m2/repository/io/dapr
	cp -r ~/.m2/repository/io/dapr/* .m2/repository/io/dapr/
	docker build -t localhost:5001/pubsub-pulsar-reproducer:latest .
	docker push localhost:5001/pubsub-pulsar-reproducer:latest
	
send:
	curl -XPOST localhost:8080/send/hello

send-raw:
	curl -XPOST localhost:8080/send/hello?rawPayload=true

bulk-topic:
	curl -XPOST localhost:8080/bulk/topic/hello

bulk-bulk:
	curl -XPOST localhost:8080/bulk/bulk/hello

start-workflow:
	curl -XPOST localhost:8080/workflow

event-workflow:
	curl -XPOST localhost:8080/workflow/event
