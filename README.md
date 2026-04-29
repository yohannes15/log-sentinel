# Live Log Sentinel

a Cats Effect CLI that scans log files, summarizes what it finds

## Setup

You can open the project in the devcontainer or run it with plain Docker Compose.

- Devcontainer: open the folder in Cursor and choose the container workflow.
- Docker Compose: `docker compose up -d`
- Shell inside the container: `docker compose exec dev bash`
- sbt version check: `sbt sbtVersion`

## Goal

Build a command-line tool that can process logs in **two formats**:

- **Plain text** lines with a fixed structure.
- **JSONL** files, where each line is one JSON object.

Both formats should decode into the same internal domain model so the reporting logic stays shared.

## Why this project

- It gives you a concrete reason to use `IO`, `Resource`, `Ref`, fibers, cancellation, and concurrency.
- It is more interesting than a toy exercise, but still smaller than a web service.
- It gives you a natural reason to learn `circe`.

## Input formats

### 1. Text mode

Use a strict, boring, easy-to-parse text format. One log entry per line:

```text
2026-04-27T19:55:10Z INFO api user logged in
2026-04-27T19:55:11Z WARN db retrying connection
2026-04-27T19:55:12Z ERROR api request failed
```

Suggested fields:

- `timestamp`
- `level`
- `source`
- `message`

### 2. JSONL mode

One JSON object per line:

```json
{"timestamp":"2026-04-27T19:55:10Z","level":"INFO","source":"api","message":"user logged in"}
```

This mode is where `circe` fits naturally.

## Suggested shape

- Scan one file or many files.
- Count errors, warnings, infos, and other notable patterns.
- Group results by source, file, or level.
- Report malformed lines without failing the whole run.
- Optionally stream progress while the scan is running.
- Optionally watch a directory for new log files.


## Milestones

1. Define the problem and the report shape.
2. Add argument/config parsing.
3. Model the shared log domain and error cases.
4. Add text parsing for the fixed log format.
5. Add JSONL parsing with `circe`.
6. Add a safe scanning pipeline.
7. Add concurrency and cancellation where it helps.
8. Add tests once the shape stabilizes.

