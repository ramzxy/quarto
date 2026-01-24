# Quarto Game Project - Group 109

## Project Overview

This project contains the implementation of the Quarto game, including a Server, a Human Client (TUI), and an AI Client.

## Structure

- `src/`: Source code for the project.
- `test/`: Unit tests.
- `docs/`: Generated Javadoc documentation.
- `dist/`: Executable JAR files (Server, Client, AI Client).

## Prerequisites

- Java 17 or higher.
- Gradle (provided via wrapper).

## How to Build and Run Tests

The project uses Gradle for building and testing.

### Build and Generate JARs

To build the project and generate the JAR files in the `dist` directory:

```bash
./gradlew build
```

This will create:

- `dist/server.jar`
- `dist/client.jar`
- `dist/ai-client.jar`

### Run Tests

To run the JUnit tests:

```bash
./gradlew test
```

### Generate Documentation

To generate Javadoc:

```bash
./gradlew javadoc
```

The documentation will be available in `docs/javadoc`.

## Running the Applications

### Server

**Main Class:** `Server.ServerApplication`

To run the server:

```bash
java -jar dist/server.jar
```

Or directly via Gradle (if configured) or class path, but using the JAR is recommended.

### Human Client

**Main Class:** `Client.ClientApplication`

To run the human client (TUI):

```bash
java -jar dist/client.jar
```

### AI Client

**Main Class:** `Client.AIClientApplication`

To run the AI client:

```bash
java -jar dist/ai-client.jar
```

## Dependencies

- **JUnit 5**: Used for unit testing. Included via Gradle dependencies.

## Authors

- Group 109
