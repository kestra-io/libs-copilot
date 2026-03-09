# Kestra main repository

[Github](https://github.com/kestra-io/kestra)

[Website](https://kestra.io)

# Kestra AI Copilot Java Library

This repository contains a Java library used to implement and centralize the full set of **Kestra AI Copilot-related services**.

It is intended to be the shared foundation for Copilot features so service integrations, business logic, and reusable components can live in one coherent place.

## Purpose

- Provide a dedicated Java library for Kestra AI Copilot capabilities.
- Centralize Copilot service contracts and implementations.
- Reduce duplication across projects consuming Copilot functionality.

## Project Structure

- `lib/`: Main Java library module.
- `lib/src/main/java/io/kestra/libs/copilot`: Library source code.
- `lib/src/test/java/io/kestra/libs/copilot`: Unit tests.

## Build & Test

From the repository root:

```bash
./gradlew build
```

Run tests only:

```bash
./gradlew test
```

## Technology

- Java (Gradle `java-library` project)
- JUnit 5 for testing
- Gradle Wrapper for reproducible builds

## Roadmap

This library is the base for:

- Copilot service orchestration
- Shared domain/service abstractions for AI-assisted flows
- Future integrations and reusable Copilot modules in the Kestra ecosystem

## License

This project is licensed under the terms of the [LICENSE](LICENSE) file in this repository.
