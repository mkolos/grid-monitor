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
- Connection pool sizing relative to worker pool size — now more relevant
  since consumer workers run on virtual threads and `workerCount` is no
  longer bounded by platform-thread cost.
