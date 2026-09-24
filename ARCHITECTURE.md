# Grid Monitor — High-Level Architecture

Learning project: a smart grid telemetry system for Vienna's electricity network.
Goals: Java, multithreading, and Kafka message consumption, built as a small
but realistic multi-component system.

## Components (4 Gradle modules, one repo)

```
grid-monitor/
├── producer/    Spring Boot + spring-kafka. Simulates 2,300 meters.
├── consumer/    Spring Boot + spring-kafka. Kafka -> worker pool -> DB.
├── api/         Spring Boot + webmvc. Read-only queries for the frontend.
└── frontend/    Not designed yet — out of scope until the backend works.
```

Consumer and API are separate modules on purpose: consumer only ever writes
to the DB, API only ever reads. They can be run, scaled, and reasoned about
independently.

## Scenario / scale

- 23 districts x 100 meters = 2,300 meters total.
- Each meter emits one reading every 5 seconds -> ~460 msg/s aggregate.
- Reading fields: `meterId`, `district`, `powerConsumptionKW`, `voltage`, `timestamp`.

## End-to-end flow

```
producer                    Kafka topic                  consumer                                    DB
                          "electricity-readings"
+-----------------+      +------------------+      +------------------------------------+
| ScheduledExec-   |      |  12 partitions   |      | Listener threads (poll partitions)|
| utorService pool |----->|  key = meterId   |----->|         |                          |
| (~2,300 periodic |      |  ~460 msg/s      |      |         v                          |
|  tasks, 1/meter/ |      +------------------+      |  router: hash(meterId) % workers  |
|  5s)             |                                |         |                          |
+-----------------+                                |   +-----+-----+-----+-----+        |
                                                     |   v           v     v     v        |
                                                     | queue0     queue1 ... queueN       |
                                                     | (bounded, blocking put)             |
                                                     |   |           |     |     |        |
                                                     |   v           v     v     v        |
                                                     | worker0    worker1 ... workerN -----|--> electricity_data (insert)
                                                     +------------------------------------+   latest_reading (upsert, guarded)
                                                                                                    |
                                                                                     api <-----------+  GET /districts/live
                                                                                      |               SUM(power) GROUP BY district
                                                                                      v
                                                                                  frontend (table)
```

**Key invariant**: the router hashes `meterId` to a fixed worker index, so
every message for a given meter always lands on the same worker thread.
This preserves the per-meter ordering Kafka already guarantees (via
partitioning by `meterId`) all the way through to the DB write — a shared
thread pool draining one queue would NOT have this guarantee.

## Kafka topic

- Name: `electricity-readings`
- Partitions: 12
- Key: `meterId`
- Value: JSON (Avro/Schema Registry is a reasonable later upgrade, not needed now)

## Consumer concurrency model

- Listener thread(s) only poll and route — they never touch the DB directly.
- Router: `hash(meterId) % numWorkers` picks a fixed worker per meter.
- Each worker owns one bounded `BlockingQueue`. `put()` blocks when full,
  which is the backpressure mechanism — it naturally slows Kafka consumption
  to match DB write speed.
- Each worker thread drains its own queue and writes one row at a time,
  immediately (no batching, at least until we've measured whether it's a
  bottleneck at ~460 msg/s).
- Workers run on **virtual threads** (`Executors.newVirtualThreadPerTaskExecutor()`).
  A worker spends nearly all its life parked — on `queue.take()` or inside
  the blocking JDBC write — which is exactly the shape of workload virtual
  threads are for. This is a pure executor swap: the hash routing, the
  queues, and the ordering guarantee are unchanged, since that guarantee
  comes from the queue/worker topology, not from the kind of thread each
  worker runs on.
- One consequence: `workerCount` used to be capped by platform-thread cost.
  With virtual threads that cap is gone, so the real ceiling on concurrent
  DB writers is now the JDBC connection pool (HikariCP, default max size
  10, currently unconfigured). Raising `workerCount` well past the pool
  size wouldn't error, but writes would start queuing invisibly inside
  Hikari instead of in the `BlockingQueue` you can actually see. Not an
  issue at the current `workerCount=8`; worth revisiting together if that
  number ever goes up (see Parking lot).

## Producer concurrency model

- One `ScheduledExecutorService` (small pool, e.g. 8–16 threads).
- ~2,300 independently scheduled periodic tasks, one per meter, each firing
  every 5s. The pool multiplexes far more logical tasks than OS threads —
  the core lesson here.
- **Deliberately still platform threads**, unlike the consumer's worker
  pool (see below). Two reasons this module doesn't get the same
  virtual-thread treatment:
  - The JDK has no virtual-thread `ScheduledExecutorService` —
    `Executors.newVirtualThreadPerTaskExecutor()` only produces a plain
    `ExecutorService`, so `scheduleAtFixedRate` has no direct
    virtual-thread equivalent to swap in.
  - More fundamentally, a tick's work here doesn't block: it computes the
    next reading (CPU) and calls `KafkaTemplate.send()`, which is
    asynchronous and returns immediately. Virtual threads are cheap
    specifically because a *parked* virtual thread costs ~nothing extra;
    with nothing here that parks, there's no cost to amortize. Contrast
    with the consumer worker, which spends nearly all its time blocked on
    `queue.take()` or a blocking JDBC write — that's the shape of workload
    virtual threads are for.

## Observability (consumer)

- Consumer exposes Prometheus-format metrics at `/actuator/prometheus`
  (via `micrometer-registry-prometheus`; `management.endpoints.web.exposure.include=health,prometheus`).
  This is metrics-first, infra-second: no Prometheus/Grafana containers
  yet, just the endpoint, curl-verified by hand. Prometheus+Grafana as
  scrape/dashboard infra is a separate future step.
- Adding this required adding `spring-boot-starter-web` to `consumer`,
  which previously had no embedded web server at all — Actuator's HTTP
  endpoints (including `/actuator/prometheus`) can't be served without
  one; there's no way around this if metrics need to be scraped over
  HTTP. Consumer is now a "web app" solely to serve this endpoint; it has
  no REST controllers.
- Four custom metrics, all tagged by `worker` (the router's per-worker
  hash index), built once at startup per worker
  (`consumer/src/main/java/com/smartgrid/consumer/routing/WorkerMetrics.java`)
  rather than touching the registry on the hot path:
  - `consumer.queue.depth` (gauge) — live size of each worker's
    `BlockingQueue`, the direct visibility into the backpressure mechanism.
  - `consumer.db.write.duration` (timer) — wall-clock time of each
    `ReadingRepository.save()` call.
  - `consumer.readings.processed` / `consumer.readings.dropped`
    (counters) — success/failure per worker; `dropped` turns what used to
    be a log-only failure mode into an alertable time series.
- Measured 2026-09-24 under real producer traffic (~460 msg/s): DB write
  latency averaged **~1.5ms** per write, queue depth sat at **0** on all 8
  workers throughout, and processed counts were evenly spread
  (~1825–1894 per worker). This is real confirmation of the batching
  parking-lot entry below — the DB write path has substantial headroom at
  current load.

## DB schema

| Table | Columns | Notes |
|---|---|---|
| `districts` | `id` (PK), `district_number`, `name` | 23 rows, seeded at startup |
| `electricity_data` | `id` (PK), `meter_id`, `district_id` (FK), `power_consumption_kw`, `voltage`, `timestamp` | append-only history |
| `latest_reading` | `meter_id` (PK), `district_id` (FK), `power_consumption_kw`, `voltage`, `timestamp` | 2,300 rows, upserted with `WHERE excluded.timestamp > latest_reading.timestamp` guard against out-of-order writes |

## API

- `GET /districts/live` — for each district, sum `power_consumption_kw`
  across `latest_reading` (one row per meter), grouped by district.

## Parking lot (deferred, not yet decided)

- Frontend tech stack.
- Message serialization upgrade (Avro/Schema Registry) if JSON becomes a pain.
- DB write batching, if per-row inserts turn out to bottleneck at real load.
  Revisited 2026-09-24: at ~460 msg/s / 8 workers (~57 readings/sec/worker),
  per-row writes are nowhere near a measured bottleneck on local Postgres,
  so batching was deferred again rather than added speculatively. If it
  comes back, the design already discussed is a hybrid — a real JPA
  `@Entity` + Hibernate batch insert for `electricity_data` (append-only,
  fits the ORM lifecycle), but `latest_reading`'s conditional upsert
  (`WHERE excluded.timestamp > latest_reading.timestamp`) stays native
  batched SQL (`JdbcTemplate.batchUpdate`), since plain JPA has no concept
  of a conditional upsert.
- Connection pool sizing relative to worker pool size — now more relevant
  since consumer workers run on virtual threads and `workerCount` is no
  longer bounded by platform-thread cost.
