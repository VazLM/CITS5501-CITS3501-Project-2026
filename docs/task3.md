# Task 3 - Fixtures and test doubles

## Fixtures as a source of truth

Repeatable scraper tests should use small, version-controlled fixtures whose
expected results are known in advance. Useful fixtures include a normal HTML
index page, an index page with changed markup, truncated HTML, a valid PDF, an
HTML error page returned from a `.pdf` URL, and a corrupt or incomplete PDF.
The expected links, status, content type, file signature and error outcome can
be recorded with each fixture. A local HTTP server can then serve the same
bytes and headers on every test run, so a result does not depend on changes to
the live website or the network.

## Observations about request limits

We made one request to the supplied policy index with `curl`, asking it to
discard the response body and display the response headers:

```bash
curl -sS -D - -o /dev/null https://uwa-policy.cits5501.arranstewart.io/policy/
```

The request returned HTTP `200`. The response included
`X-Ratelimit-Limit: 3`, `X-Ratelimit-Remaining: 2` and `Retry-After: 10`.
This is direct evidence that the service advertises a low request limit and a
ten-second retry interval. We stopped after identifying the limit rather than
deliberately exhausting it.

Even when a small probe succeeds, the full test suite should run against local
fixtures and test doubles. This avoids unnecessary traffic, makes tests fast
and repeatable, and allows failure conditions to be created safely.

## Test doubles in Java

Java test doubles can simulate responses that are difficult to obtain reliably
from the live site. The JDK's `com.sun.net.httpserver.HttpServer` is sufficient
for a small local fake server. Larger automated suites could use WireMock or
OkHttp MockWebServer to return chosen status codes, delay a response, send an
incomplete body or close a connection. JUnit 5 can run the tests and make
assertions, while Mockito can replace internal collaborators when the scraper
is structured to accept injected dependencies.

Timeouts can be simulated by delaying the response longer than the scraper's
request timeout. Interrupted downloads can be simulated by declaring a body
length and closing the connection before all bytes are sent. Connection
failures can be tested by connecting to an unused local port. Slow responses
can be produced by sending a response body in small chunks with a delay between
them.

## Demonstrated failure scenario

The included `task3/BadPdfServer.java` is a test double for the Task 2 scenario
in which a PDF link returns HTML. Its index page links to
`/fake-policy.pdf`, so the prototype accepts the link based on its extension.
The download endpoint deliberately returns HTTP `200` and
`Content-Type: text/html`, with an HTML body. Running the scraper against this
server shows `successful HTTP response: yes`, even though the response is not a
PDF. The content type and initial bytes provide evidence of the mismatch. This
is a failed content-validation test: the expected behaviour is to reject the
response as an invalid PDF, while the observed prototype only reports that the
HTTP request succeeded. No Java exception is expected because the fake server
deliberately returns a well-formed HTTP response.

This scenario is undesirable to reproduce on the live site because the team
does not control that server and should not replace a real policy document with
incorrect content. The local test double is safe, deterministic and can be run
repeatedly without sending test traffic to the production-like service.
