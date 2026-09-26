# Handling Unprocessable Kafka Messages

General background on what a Kafka consumer should do with a message it cannot process: one
it can't deserialize, one that is empty (a tombstone), or one whose structure is broken.
It covers the options, what common practice recommends, and when each one fits. This file
is project-independent on purpose. How this codebase applies it is described in
[`validation.md`](validation.md).

---

## The problem: the "poison pill"

A Kafka consumer reads each partition in order and commits an offset to record how far it has
got. If one record can't be processed, the consumer faces a choice. It either moves past the
record, which loses it or sets it aside, or it stays on the record, which blocks the partition.
A record that fails in the same way every time it is read is called a **poison pill**.

Two kinds of failure behave differently:

- **Non-retryable failures.** The bytes themselves are the problem: invalid JSON, a field of the
  wrong type, an incompatible schema, a missing required structure. The record never changes, so
  retrying it gives the same failure every time.
- **Retryable (transient) failures.** Something outside the record is the problem: a database is
  down, a downstream service times out. Retrying later can succeed.

This document is about the first kind. For transient failures, retrying (with backoff, possibly
through retry topics) is the usual answer.

A third kind, messages that are read successfully but break a business rule, is covered in
"Business rejections are not technical failures" below.

## What "stopping the channel" means

Most Kafka client frameworks stop by default when they hit a record they can't handle. In
practice, stopping looks like one of these:

- **Stuck consumer.** The consumer keeps re-reading the same record and never commits past it.
  The process stays up, but that partition makes no progress.
- **Terminated consumer.** The framework cancels the consuming stream, and the consumer leaves
  its consumer group. Nothing more is consumed on that topic until the application restarts.

Both have the same consequences:

- **The restart doesn't help.** The offset was never committed, so after a restart the consumer
  reads the same record and fails again. Someone has to step in and move the offset past it by
  hand, or remove its cause.
- **Every later message is blocked too.** Valid records behind the poison pill on the same
  partition wait as well, not just the bad one.
- **The failure is easy to miss.** The rest of the application (HTTP endpoints, other consumers)
  keeps running. Unless consumer health or consumer lag is monitored and alerted on, nobody
  notices that a topic stopped being processed.
- **Other consumers are disturbed.** When consumers share a consumer group, a consumer leaving
  causes a rebalance, which briefly pauses the others as well.

## The options

| Option | What happens to the bad record | What happens to later records |
|---|---|---|
| **Stop** (fail-fast) | Stays at the head of the partition | Blocked until a person intervenes |
| **Log and skip** | Logged, then gone | Processed normally |
| **Dead-letter queue (DLQ)** | Written, raw and unchanged, to a separate topic along with error details | Processed normally |

A DLQ keeps the record's original bytes, usually with headers describing the failure: the
reason, the exception, and the source topic, partition and offset. Someone can inspect the
record there, fix whatever was wrong (the producer, the consumer, or the data), and replay it.

## What common practice says

- **Stopping doesn't help with a non-retryable failure.** The record never changes, so staying on
  it achieves nothing except blocking everything behind it. It only helps if a person steps in,
  and in the meantime every valid message waits as well.
- **The major frameworks all offer a way not to die, and most recommend it.** Each of these has
  a switch to stop deserialization failures from killing the consumer, plus a way to route the
  record elsewhere:
  - Spring Kafka: `ErrorHandlingDeserializer` + `DeadLetterPublishingRecoverer`.
  - Kafka Connect: `errors.tolerance=all` + `errors.deadletterqueue.topic.name`.
  - SmallRye Reactive Messaging: `fail-on-deserialization-failure=false` +
    `failure-strategy=dead-letter-queue`.
- **The recommended default is a DLQ, not log-and-skip.** Log-and-skip usually loses the record's
  content: the log line says that something failed, but the original payload is gone, and the
  data can't be recovered or replayed. A DLQ keeps both properties that matter: the consumer
  keeps running, and nothing is lost.
- **A DLQ needs monitoring.** Records only land in a DLQ when something is wrong. Alerting on
  DLQ volume, or on the rate of invalid messages, is what turns a DLQ from a place where records
  are quietly discarded into a working error channel. Someone must also own the process of
  reviewing and replaying DLQ records.

## When stopping *is* the right choice

- **Order matters and a gap corrupts state.** Examples: event sourcing, account balances, or any
  stream where each event only makes sense after all the ones before it. Skipping one event makes
  every later state wrong, and often in a way nobody can see. Stopping and fixing things by hand
  is safer. So is a DLQ that deliberately also holds back the records that come after the bad
  one, for example those with the same key.
- **A missing record is worse than a delay.** Some records must never be lost, and processing can
  wait until a person looks at the problem. For example, compliance or financial settlement where
  "processed later" is acceptable but "not processed" is not.

## The "everything starts failing" risk

Skip and DLQ both carry one specific risk. If a producer ships a breaking change, such as a
renamed field, a new format or a schema mismatch, then *every* record fails, not just an
occasional one. With log-and-skip this becomes a silent mass data loss. Stopping would at
least make the problem obvious.

The usual answer is not to stop. It is the combination of:

- a **DLQ**, so nothing is lost and everything can be replayed once the incompatibility is fixed,
  and
- **alerting on the invalid or DLQ rate**, so a spike is noticed within minutes, not days.

Some teams add a circuit breaker on top: if the failure *rate* goes over a threshold, stop after
all, on the basis that a sudden flood of failures means something is broken systemically rather
than one bad message.

## Replaying DLQ records

"Replay" means writing a DLQ record back to the topic it came from, so that the consumer
processes it again. A DLQ record normally carries what that needs: the original key and value,
and headers naming the source topic, partition and offset, plus the failure reason. The exact
header names depend on the framework.

### Fix first, then replay

For a non-retryable failure, replaying the unchanged record to the unchanged consumer just fails
again and puts the record back in the DLQ. One of these has to happen first:

- **The consumer is fixed.** For example, it now accepts the new format. Replay the records
  unchanged after the fix is deployed.
- **The payload is fixed.** For example, a producer bug wrote a wrong field. The replay step
  transforms each record, or someone edits it by hand, before writing it back.
- **The producer is fixed and the data is resent from its source.** In that case the DLQ records
  aren't replayed at all. They only serve as a list of what went missing.

### Ways to replay

- **Command-line tools, for a handful of records.** Read a record from the DLQ and write it to
  the original topic. For example, with `kcat`:
  ```sh
  kcat -C -b <broker> -t <topic>-dlq -o <offset> -c 1 -e -K '|' \
    | kcat -P -b <broker> -t <topic> -K '|'
  ```
  The `-K` separator keeps the key, so the record lands on the same partition as before.
- **Kafka web UIs, for the same case.** Tools such as Redpanda Console, Kafka UI (Kafbat),
  Conduktor or AKHQ can show a DLQ record and publish it, optionally edited, to another topic.
- **A replay job, for many records or regular use.** A small tool or admin endpoint that works
  like this:
  1. It consumes the DLQ with its own consumer group.
  2. It selects records, for example by offset range, time window or failure reason.
  3. It optionally transforms them.
  4. It writes each one to the topic named in its source-topic header, with the original key.
  5. It commits its offset, so a record is never replayed twice by accident.

  Adding a header such as `replayed-from-dlq-offset` makes replayed records easy to trace.

### What to watch for

- **Ordering.** A replayed record goes to the *end* of the topic, after records that were
  produced later. That is harmless for independent messages. For ordered streams, see "When
  stopping *is* the right choice" above.
- **Duplicates.** If the consumer had side effects before it failed (a database write, an
  outgoing call), replaying repeats them. Consumers should be idempotent, or should fail before
  any side effect.
- **Loops.** A record that fails again goes back to the DLQ. Keep a replay counter in a header,
  or rely on a person to check, so the same record doesn't cycle forever.
- **Retention.** A DLQ is an ordinary topic with a retention limit. Set it long enough for
  someone to notice the records and act on them. Records can't be deleted from a topic one by
  one, so track "already handled" separately, for example through the replay job's committed
  offset.

## Business rejections are not technical failures

Some messages are read successfully but break a business rule: a quantity out of range, a
required value left blank, a reference to something unknown. These are different from the
failures above. The consumer understood the message and decided "no". That is an expected,
handled outcome, not a sign that something is broken.

| | Technical failure | Business rejection |
|---|---|---|
| What went wrong | The bytes can't be interpreted | The content was understood and breaks a rule |
| Expected? | No, it points to a bug or an incompatibility | Yes, the code handles it explicitly |
| Replay unchanged | Works once the consumer is fixed | Fails again unless the *rule* changes |

### The argument for treating them the same

A Kafka message usually describes something that has already happened, such as "goods
arrived" or "item sold". It is not a request that can be refused. An HTTP endpoint can answer
`400` and the caller corrects and resends. A consumer that rejects an event has no such caller,
and the event happened anyway. If the rule turns out to be wrong, the rejected events are
missing from the consumer's state. Recovering them should be a replay, not a manual
reconstruction. The producer also never learns about the rejection unless something tells it.

### The argument for keeping them apart

- **The DLQ loses its meaning.** Its alert should mean something is broken. If routine
  business rejections land there too, a real incident is lost among expected rejects, and the
  alert either fires constantly or needs a threshold too high to be useful.
- **The data may already be kept.** If the rejection is logged with the full message content
  (easy for small messages), nothing is lost. Losing the payload is what justified a DLQ in the
  first place, and that concern doesn't apply.
- **Routing to a DLQ usually means throwing.** Most frameworks send a message to the DLQ when it
  is nacked, typically because of an exception. Throwing for an expected outcome produces
  error-level logs with stack traces for every business reject. Nacking explicitly avoids the
  exception but makes the consumer code more complex.
- **Different people read them.** The people who care about business rejections read the
  application's own records, such as an audit log or an admin view. Operations reads the DLQ.

### Common patterns

There is no single standard. Practice converges on two principles: **never lose data silently**,
and **keep technical failures and business rejections separate**. The usual options are:

- **Log or audit, then skip.** Fine when the log entry captures the whole message and replay is
  rarely needed.
- **A separate "rejected" topic** (e.g. `<topic>-rejected`), apart from the DLQ. Rejections are
  kept and replayable, with their own alert or dashboard, and the DLQ still means "something is
  broken". The reason for rejection goes in headers, as it does for DLQ records.
- **A rejection event back to the producer** (e.g. `SomethingRejected`). This is the
  event-driven equivalent of a `400`. It is the cleanest solution when producer and consumer are
  owned by different teams or organizations, because the owner of the data is told and can fix
  it at the source.

These options combine. Keeping the audit or log entry alongside a rejected topic or a rejection
event is common, because business users rely on it.

## Decision guide

1. **Are the messages independent of each other?** Each message's effect stands alone, and order
   between messages doesn't affect correctness. If yes, don't stop. Use a DLQ, and keep an audit
   or log entry as well if that suits the system.
2. **Does a gap break later processing?** If yes, stop, or use a DLQ that also holds back the
   records that depend on the bad one.
3. **Is the message readable, but it breaks a business rule?** Then it is not a DLQ case. Log
   or audit it with its full content, send it to a separate rejected topic, or send a rejection
   event back to the producer (see "Business rejections are not technical failures").
4. **In every case:** alert on the failure rate, and document who reviews the DLQ and how
   records are replayed.

## References

- Confluent, "Error Handling Patterns in Kafka" and the Kafka Connect error-handling / DLQ
  documentation.
- Spring for Apache Kafka reference, "Handling Deserializer Exceptions" and
  "Publishing Dead-letter Records".
- SmallRye Reactive Messaging Kafka connector documentation, "Handling deserialization failures"
  and "Failure Management" (`fail`, `ignore`, `dead-letter-queue`, `delayed-retry-topic`).
