# spring-boot-kafka-microservices

A small Spring Boot + Apache Kafka demo showing the **pub-sub / fan-out** messaging pattern: a single producer publishes an event to one Kafka topic, and multiple independent consumers each react to it on their own, with no coordination or communication between them.

This is intentionally the simplest of a set of related demo repos. Unlike its siblings [`springboot-kafka-choreography`](https://github.com/nitin0939/springboot-kafka-choreography) and [`springboot-kafka-orchestration`](https://github.com/nitin0939/springboot-kafka-orchestration), which implement full saga patterns (event chaining between services, compensating transactions, etc.), this project has **no saga logic** — `stock-service` and `email-service` don't talk to each other or back to `order-service`; they simply listen to the same topic independently.

## How it works

1. A client sends `POST /api/v1/orders` to `order-service` with an order payload.
2. `order-service` wraps it in an `OrderEvent` (status `PENDING`) and publishes it to the `order_topics` Kafka topic via `OrderProducer` (a `KafkaTemplate`).
3. `stock-service` and `email-service` each run their own `OrderConsumer` (`@KafkaListener`) subscribed to `order_topics`, in separate consumer groups (`stock` and `email` respectively) — so both receive and process every event independently.

```
                     order_topics (Kafka topic)
                            ▲
                            │ publish
                 ┌──────────┴──────────┐
                 │    order-service     │  POST /api/v1/orders
                 └──────────────────────┘
                            │
              ┌─────────────┴─────────────┐
              │ consume (fan-out)         │ consume (fan-out)
              ▼                           ▼
     ┌──────────────────┐        ┌──────────────────┐
     │   stock-service   │        │   email-service   │
     └──────────────────┘        └──────────────────┘
```

## Modules

This is a Maven project made up of four independent modules (each has its own `pom.xml` / `spring-boot-starter-parent`; there is no aggregator/reactor `pom.xml` at the root, so each module is built and run on its own):

- **`base-domains`** — shared library module with the common DTOs used across services: `Order` (the order payload) and `OrderEvent` (the message envelope: `status`, `message`, and the `Order`). Consumed by all three services as a Maven dependency.
- **`order-service`** — REST producer. Exposes `POST /api/v1/orders`, builds an `OrderEvent`, and publishes it to Kafka via `OrderProducer` / `KafkaTemplate`. Also declares the `order_topics` topic (`KafkaTopicConfig`). Runs on the default port `8080`.
- **`stock-service`** — Kafka consumer. `OrderConsumer` listens on `order_topics` (consumer group `stock`) and logs the received event (a stand-in for e.g. decrementing stock). Runs on port `8081`.
- **`email-service`** — Kafka consumer. `OrderConsumer` listens on `order_topics` (consumer group `email`) and logs the received event (a stand-in for e.g. sending a confirmation email). Runs on port `8082`.

Tech stack: Java 11, Spring Boot 3.5.14, Spring Kafka, Lombok, Maven.

## Running it

**1. Start a Kafka broker.** There's no `docker-compose`/`compose.yaml` bundled in this repo, so bring up Kafka yourself, e.g. with the Confluent quickstart image:

```bash
docker run -d --name broker -p 9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  confluentinc/cp-kafka:latest
```

All services expect Kafka at `localhost:9092` (see each module's `application.properties`) — adjust `spring.kafka.*.bootstrap-servers` if yours runs elsewhere.

**2. Build and run each module** (in separate terminals, `base-domains` first since the services depend on it):

```bash
cd base-domains   && mvn clean install
cd ../order-service  && mvn spring-boot:run   # :8080
cd ../stock-service  && mvn spring-boot:run   # :8081
cd ../email-service  && mvn spring-boot:run   # :8082
```

**3. Place an order:**

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"name": "Keyboard", "qty": 1, "price": 49.99}'
```

Watch the `stock-service` and `email-service` logs — both should independently log receipt of the same `OrderEvent`.

## Further reading

Detailed architecture notes: [`Architecture Kafka.docx`](./Architecture%20Kafka.docx) (included in this repo).
