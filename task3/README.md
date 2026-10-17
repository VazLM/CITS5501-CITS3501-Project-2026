# Task 3 failure-scenario test double

`BadPdfServer.java` is a small local HTTP server for the Phase 2 Task 3
artifact. It deliberately serves HTML from a URL ending in `.pdf`. This
reproduces the scenario where a policy-document link returns a response that
is not a valid PDF.

The server uses only JDK classes and requires Java 17 or later. It does not add
any Maven dependencies.

## Run the test double

From the repository root, run:

```bash
java task3/BadPdfServer.java
```

The server listens only on `localhost:8000`. Leave it running while testing the
scraper. Stop it with `Ctrl+C`.

## Run the scraper against it

Open a second terminal in the repository root.

On Windows:

```powershell
mvnw.cmd -q compile exec:java -Dexec.args="http://localhost:8000/"
```

On macOS or Linux:

```bash
./mvnw -q compile exec:java -Dexec.args="http://localhost:8000/"
```

## Expected evidence

The scraper identifies `/fake-policy.pdf` as a policy link because its path
ends in `.pdf`. The server then returns status `200` with content type
`text/html`. The scraper output should therefore include values similar to:

```text
successful HTTP response: yes
HTTP status: 200
content type: text/html; charset=utf-8
first 16 bytes: <!doctype html>
```

The important failure is not a Java exception or a failed network request. It
is the mismatch between the expected and observed behaviour:

| Check | Result |
| --- | --- |
| Expected | Reject the response because it is not a valid PDF. |
| Observed | The prototype reports `successful HTTP response: yes`. |
| Test result | **FAIL - invalid PDF content was not rejected.** |

This demonstrates that a successful HTTP response and a filename extension do
not prove that the response body is a valid PDF. The Task 1 prototype prints
enough metadata to expose the problem, but it does not perform this validation.

## Verification

The artifact was compiled and run with Eclipse Temurin JDK 17.0.20.1. The
existing Java scraper was then run against the local server and produced the
evidence shown above.
