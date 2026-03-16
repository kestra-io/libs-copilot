# Agent Guide for libs-copilot

This file is for agentic coding tools working in this repository.
Keep changes minimal, follow existing patterns, and prefer clarity.

## Repository overview
- Java library for Kestra AI Copilot services.
- Single Gradle project (root project name: "copilot").
- Main code under `src/main/java`.
- Tests under `src/test/java` (JUnit 5).

## Build, lint, and test commands
Run from repo root.

### Build
- Full build: `./gradlew build`
- Clean build: `./gradlew clean build`

### Tests
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests "io.kestra.libs.copilot.services.ai.AbstractAiCopilotTest"`
- Single test method: `./gradlew test --tests "io.kestra.libs.copilot.services.ai.AbstractAiCopilotTest.generateYamlHappyPathStripsCodeBlockMarkers"`

### Lint / formatting
- No dedicated lint or formatter task found in Gradle scripts.
- Keep formatting consistent with existing code and Gradle Kotlin DSL.

### Useful Gradle flags
- Stack traces: `./gradlew test --stacktrace`
- Info logs: `./gradlew test --info`
- Debug logs: `./gradlew test --debug`

## Toolchain and build constraints
- Java toolchain is set to JDK 25.
- Compiled bytecode targets Java 21 (`options.release = 21`).
- Use Gradle wrapper (`./gradlew`) for reproducibility.

## Code style guidelines

### Formatting
- Indent with 4 spaces.
- Opening braces on the same line.
- One statement per line; avoid dense chaining when it hurts readability.
- Use Java text blocks (`"""`) for multi-line prompt strings.

### Imports
- Order: standard imports first, then static imports.
- Group with a blank line between normal and static imports.
- Avoid wildcard imports.

### Naming
- Classes and interfaces: `PascalCase`.
- Methods and fields: `camelCase`.
- Constants: `UPPER_SNAKE_CASE`.
- Generic types: `T`, `V`, `K`, `R` (single-letter or meaningful).

### Types and immutability
- Prefer `final` fields where practical.
- Use records for simple data carriers (see `PluginMetadata`).
- Use `Optional` only for return values, not for fields.
- Avoid raw types; keep generics explicit.

### Nullability and validation
- Use Jakarta validation annotations on input models: `@NotNull`, `@NotBlank`.
- Avoid implicit nulls in core service code; validate early.

### Error handling
- Use `AiException` for AI-response errors intended to surface to callers.
- Throw `IllegalArgumentException` for invalid arguments in utility code.
- Prefer specific exceptions over `RuntimeException` unless in test helpers.

### Streams and collections
- Prefer immutable collections (`List.of`, `Map.of`) when possible.
- When sorting, be explicit about comparator direction.
- Avoid side effects inside stream operations.

### JSON / YAML
- Use `JacksonMapper` for shared JSON/YAML configuration.
- Handle `JsonProcessingException` explicitly where appropriate.

### AI prompt builders
- Keep system prompts strict and schema-driven.
- Do not invent schema fields; follow constraints in prompt templates.
- Preserve user-provided YAML context when relevant.

## Testing conventions
- Frameworks: JUnit 5, AssertJ, Mockito.
- Test names should describe behavior, not implementation.
- Prefer AssertJ fluent assertions.
- Use Mockito for isolated unit tests; avoid heavy integration setups.
- Keep test fixtures minimal and readable.

## Gradle release task notes
- `releaseVersion` runs git commands and updates `gradle.properties`.
- Git command failures are logged with stdout and stderr.
- Do not change release logic unless required for publishing behavior.

## Cursor/Copilot rules
- No `.cursor/rules/` or `.cursorrules` found in this repo.
- No `.github/copilot-instructions.md` found in this repo.

## When in doubt
- Follow existing patterns in `src/main/java/io/kestra/libs/copilot`.
- Keep changes minimal and focused.
- Add tests when behavior changes or bug fixes are introduced.
