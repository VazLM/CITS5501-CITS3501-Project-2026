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

## Run the explicit scenario check

While the test double is still running, open a second terminal in the
repository root and run:

```bash
java task3/BadPdfScenarioCheck.java
```

This checker uses only JDK classes. It verifies the response status, declared
content type and `%PDF-` file signature, then prints an explicit result. These
two commands are enough to demonstrate the required Task 3 failure scenario;
they do not depend on Maven or any third-party library.

## Expected evidence

The checker requests `/fake-policy.pdf`. The server returns status `200` with
content type `text/html`, even though the URL ends in `.pdf`. The checker should
print:

```text
Task 3 failure-scenario check
  URL suggests PDF: true
  HTTP status: 200
  Content-Type: text/html; charset=utf-8
  Starts with %PDF-: false

RESULT: FAILURE REPRODUCED
Expected: a valid PDF response.
Observed: the .pdf URL returned HTTP 200, but the content is not a PDF.
```

The important failure is not a Java exception or a failed network request. It
is the mismatch between the expected and observed behaviour:

| Check | Result |
| --- | --- |
| Expected scraper behaviour | Reject the response because it is not a valid PDF. |
| Existing prototype behaviour | Reports HTTP success; its metadata reveals HTML content. |
| Checker result | **FAILURE REPRODUCED - the fixture returned an invalid PDF response.** |

This demonstrates that a successful HTTP response and a filename extension do
not prove that the response body is a valid PDF. The Task 1 prototype prints
enough metadata to expose the problem, but it does not perform this validation.

## Verification

Both Java files were compiled and run with Eclipse Temurin JDK 17.0.20.1. The
checker produced the exact evidence shown above. The existing Java scraper was
also run against the server; it reported a successful HTTP response while its
metadata showed `text/html` and initial bytes of `<!doctype html>`.
