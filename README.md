# Grid Monitor

A learning project built with Java, Spring Boot, and Apache Kafka.

The application simulates electricity meters, generates readings periodically, and publishes them to a Kafka topic.

## Architecture

```text
MeterSimulator
      |
      v
ReadingPublisher
      |
      v
Apache Kafka
      |
      v
Kafka consumers
      |
      v
Database
      |
      v
REST API
```

## Technologies

- Java 21+
- Spring Boot
- Apache Kafka
- Spring Kafka
- Gradle

## Prerequisites

- Java 21 or newer
- Apache Kafka
- Gradle
- Docker

## Running the Application

Start Kafka and the other services defined in `docker-compose.yml`:

```bash
docker compose up -d
```

Start the producer

```bash
./gradlew :producer:bootRun
```

Start the consumer

```bash
./gradlew :consumer:bootRun
```

Start the API

```bash
./gradlew :api:bootRun
```

## Configuration

Example configuration:

```properties
grid.producer.topic=meter-readings
grid.producer.interval=PT5S
grid.producer.district-count=10
grid.producer.meters-per-district=230
grid.producer.scheduler-pool-size=4
grid.producer.autostart=true
```

| Property | Description |
|---|---|
| `topic` | Kafka topic name |
| `interval` | Time between readings |
| `district-count` | Number of districts |
| `meters-per-district` | Number of meters per district |
| `scheduler-pool-size` | Number of scheduler threads |
| `autostart` | Whether the simulation starts automatically |

## Testing

Run the tests with:

```bash
./gradew test
```

To disable the simulator during tests:

```properties
grid.producer.autostart=false
```