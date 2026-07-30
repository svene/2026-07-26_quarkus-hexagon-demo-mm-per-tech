# quarkus-hexagon-demo-mm-per-tech

Demo system illustrating hexagonal architecture with Quarkus: one Maven module
per adapter technology, a single combined domain+application core, and one
runnable deployable (`app-server`) that wires everything together.

## Module map

| Module | Role | Technology |
|---|---|---|
| `core` | Domain + application (use cases + all ports) | plain Java + CDI only |
| `adapter-inbound-rest` | Inbound adapter | JAX-RS (Quarkus REST) |
| `adapter-inbound-kafka` | Inbound adapter | Kafka consumer (`@Incoming`) |
| `adapter-outbound-postgres` | Outbound adapter | Hibernate ORM w/ Panache + PostgreSQL |
| `adapter-outbound-mongodb` | Outbound adapter | MongoDB w/ Panache |
| `adapter-outbound-kafka-producer` | Outbound adapter | Kafka producer (`@Outgoing`) |
| `adapter-outbound-webservice` | Outbound adapter | SOAP client (Apache CXF) |
| `adapter-outbound-httpclient` | Outbound adapter | REST client to a remote HTTP service |
| `adapter-external-fruit-supplier-stub` | External system stub | JAX-RS endpoint → Kafka delivery event |
| `adapter-external-beverage-supplier-stub` | External system stub | SOAP endpoint (Apache CXF) → Kafka delivery event |
| `app-server` | Deployable | wires core + all adapters, no business logic of its own |

`core` depends on nothing else in this tree. Every adapter module depends only
on `core`. The two stub modules have no dependency on any adapter — they share
only a WSDL/HTTP contract on the wire. `app-server` depends on `core` and every
adapter and stub module.

## Running

Requires Docker (or Podman) running. Quarkus Dev Services will automatically
start real containers for Postgres, MongoDB, and a Kafka-compatible broker
(Redpanda by default) — no docker-compose file, no manual connection strings.

```bash
cd app-server
mvn quarkus:dev
```

## Next steps

1. Define entities and ports in `core` (e.g. `port.in` for use cases,
   `port.out` for what adapters must implement).
2. Implement one outbound adapter at a time against its port.
3. Wire the REST/Kafka inbound adapters to call the use cases.
4. Add `application.properties` entries in `app-server` as each adapter needs
   configuration (channel names, REST client keys, etc).
