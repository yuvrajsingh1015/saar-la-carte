# Saar la Carte

A tick-based restaurant simulation written in Kotlin. It reads a food catalogue,
a set of restaurants and a scenario from JSON, simulates several evenings of
service (guests arriving, tables being assigned, the kitchen cooking, drivers
delivering) and logs every event plus end-of-run statistics.

## Features

- **JSON input with schema validation** for ingredients, recipes, restaurants,
  customer groups and incidents, plus cross-checks between the files
- **Customers:** casual guests, regulars, event groups and delivery customers,
  each with food preferences, ratings and their own lifecycle
- **Front of house:** table reservations, merging tables for large groups,
  event seating, waitstaff and serving
- **Kitchen:** order queue, cooking staff, pantry with restocking and best-before dates
- **Delivery:** drivers, trips and partial deliveries
- **Incidents:** staff shortages, recipe changes, unavailable ingredients, packaging problems
- **Logging** at configurable levels and statistics per restaurant

## What I built

- Project skeleton and build setup (Gradle, detekt, CI pipeline)
- Restaurant parser and its validation
- Table reservations and table merging
- Kitchen order queue and staff management
- Delivery customers
- Event seating
- Unit tests with Mockito and scenario-based system tests
- Refactoring of the front-of-house logic to pass detekt's complexity rules
- UML class and sequence diagrams for the design

## Build and run

Requires JDK 21.

```bash
./gradlew build                  # compile, run 649 unit tests, detekt
./run-scenario.sh example 24 INFO
```

or directly:

```bash
java -jar build/libs/saar-la-carte.jar \
  --food scenarios/example/food.json \
  --restaurants scenarios/example/restaurants.json \
  --scenario scenarios/example/scenario.json \
  --maxTicks 24 --logLevel INFO
```

`scenarios/` contains around 50 test scenarios, including invalid inputs.

## Project structure

```
src/main/kotlin/saarlacarte/
  parser/       JSON parsing and schema checks
  customer/     customer group types and preferences
  restaurant/   front of house, kitchen, pantry, cooks, statistics
  delivery/     delivery service and drivers
  incidents/    incident types
  simulation/   main loop
  loggers/      event logging
src/main/resources/schema/   JSON schemas
src/test/kotlin/             unit tests
scenarios/                   input scenarios
```

## Tech

Kotlin, Gradle, kotlinx.serialization, JSON Schema, JUnit, Mockito, detekt, JaCoCo

---

<sub>Originally built as a university software engineering project (2026).</sub>
