# CITS5501/CITS3501 Project 2026 – Phase 2 Report

**Repository:** https://github.com/VazLM/CITS5501-CITS3501-Project-2026

| Name | Student Number | GitHub |
|---|---|---|
| Punit Patil | 23905993 | Punit750 |
| William Lo | 23722943 | willywonka669 |
| Lachlan Vaz | 24219564 | VazLM |
| Zhiheng Zhou | 24580295 | zhouzhiheng53-rgb |

---

## Task 1 – Prototype

### 1.1 What it does

`Scraper.java` fetches the course's policy index page, extracts every `<a href>` link on it using jsoup, and keeps only the links that (a) resolve to the same host as the index page and (b) end in one of a fixed set of document extensions (`.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`, `.odt`, `.ods`, `.odp`, `.rtf`). After deduplicating, it attempts to download the first five distinct matches and prints, for each: HTTP status, whether that status counts as success, content type, downloaded size, and the first 16 bytes of the body. A single client enforces a hard cap of 20 requests for the whole run, a 10-second timeout per request, a 1-second delay between requests, and a custom `User-Agent` identifying it as a student project.

### 1.2 Limitations

1. **Extension-based filtering only.** `isPolicyLink` decides whether a link is a policy document purely from the URL's file extension, it never checks the HTTP response's `Content-Type` header or the downloaded bytes' actual file signature. A link ending in `.pdf` that actually serves an HTML error page (or vice versa) is accepted or rejected on the strength of its name alone. This is exactly the gap Task 2/3's "malformed document" scenario (T007/T008) is designed to probe.
2. **No per-document fault isolation.** `main` calls `describeDownload` for each selected link with no `try/catch` around the loop. A timeout, a connection failure, or any `IOException` on one document propagates straight out of `main` and ends the whole run, rather than being recorded as a failure for that one document while the rest continue. There is also no retry logic to distinguish a transient 5xx from a permanent 4xx. This is the central behaviour Task 2's timeout and status-code scenarios (T003–T006) test for.
3. **No signal when the page structure changes.** Link discovery assumes the index page's existing markup (plain `<a href>` tags) stays stable. If the real site changed how it marks up policy links, the scraper would simply find zero matching links and print "0 of these look like policy documents", a normal-looking exit with no indication that something broke, which is indistinguishable from a page that legitimately has no policy documents (Task 2's T001).

These are intentional simplifications, not oversights, the brief is explicit that the prototype does not need to be robust. Tasks 2–4 reason about what a fully robust version of this same design would need to do differently.

### 1.3 Assumptions

- Policy documents are always linked from the same host as the index page; the prototype explicitly discards cross-host links (`sameHost`) before even checking the extension, so it would miss documents served from, say, a separate CDN or document store (see Task 4, A007).
- A file extension is a reasonably reliable signal of document type. The team's own probe against the real site (Task 3, §3.2) found it enforces `X-Ratelimit-Limit: 3, tighter than the 20-request budget the prototype assumes it can spend, so this assumption about the service's tolerance is already known not to fully hold; the prototype was deliberately left unchanged in response to this, consistent with the brief's scope for Task 1.
- The index page lists documents directly as `<a href>` elements reachable from a single fetch, with no pagination or JavaScript-rendered content to deal with.

---

## Task 2 – Design of Test Suite

For each of the five supplied scenarios, we first state what we consider a reasonable expectation for scraper behaviour, then provide a table of concrete test cases implementing those expectations.

### 2.1 Reasonable expectations

1. **Structure changes.** If the expected HTML structure changes, the scraper should detect the absence of matching links rather than silently proceeding as though nothing were wrong. A run that discovers zero, or an implausibly small number, of policy links should be treated as a failure condition and reported clearly, rather than logged as a successful run that simply found nothing.
2. **HTML truncated.** If the HTML returned is truncated, the parser should fail defensively rather than catastrophically. It should either recover and return whatever valid links appear before the truncation point, or raise a clearly logged parsing error, in neither case should it throw an unhandled exception or hang.
3. **Timeout (short or long).** If a request times out, whether after a short or a long period, the scraper should fail that single document rather than the whole run. The client should enforce its own timeout rather than waiting indefinitely on the server, record the affected document as failed with the reason, and continue processing the remaining links.
4. **4xx/5xx status codes.** The scraper should treat 4xx and 5xx responses as different categories of fault, consistent with the specification's own distinction between a faulty request and a faulty server. A 4xx response most likely indicates a problem with the request itself (a stale or incorrect link) and is unlikely to succeed on retry, so it should simply be recorded and skipped. A 5xx response indicates a server-side fault that may be transient, so a bounded number of retries is reasonable before the document is recorded as failed.
5. **Malformed document.** The scraper should verify a document's actual content rather than trusting its file extension. If the declared content type or the file's signature bytes do not match what the extension promised, this should be flagged as a mismatch rather than reported as a successful download.

### 2.2 Test cases

Most cases below arrange the environment with an HTTP stub returning a canned response, since we are testing how the scraper reacts to a given input rather than verifying specific calls were made; T001 and T002 instead use a local HTML fixture, since those scenarios concern parsing malformed content rather than a particular HTTP response. Task 3 discusses our use of fixtures and test doubles in more depth.

| ID   | Scenario                                 | Arrange                                                      | Act                                                          | Assert                                                       |
| ---- | ---------------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| T001 | 1 – Structure change                     | Local HTML fixture where policy links use a different tag/class pattern than the scraper targets (e.g. `<div data-doc>` instead of the expected `<a>` inside the known container) | Run link discovery against the fixture                       | Returns zero or near-zero documents and logs an explicit "no policy links found" condition; no unhandled exception, no silent "success" |
| T002 | 2 – Truncated HTML                       | Local HTML fixture cut off mid-document (closing tags and the last few `<a>` entries missing) | Feed the fixture to the parser/link-extraction step          | Completes without crashing or hanging; either returns links found before the cut-off point or raises a clearly logged parse error |
| T003 | 3a – Short timeout                       | HTTP stub throws a timeout exception for one specific URL after a short delay | Attempt that download as part of a run with other healthy links | Document recorded as failed with reason "timeout"; scraper continues to remaining documents, run does not abort |
| T004 | 3b – Hung/unresponsive link              | HTTP stub accepts the connection but never sends a response  | Attempt download with the scraper's own client-side timeout configured | Scraper's own timeout fires within its configured limit (not an indefinite wait); document recorded as failed |
| T005 | 4a – 4xx                                 | HTTP stub returns 404 Not Found for one URL                  | Attempt download                                             | Recorded as failed with status 404 in metadata; no infinite retry; scraper continues with remaining links |
| T006 | 4b – 5xx                                 | HTTP stub returns 503 Service Unavailable for one URL        | Attempt download                                             | Recorded as failed with status 503; if retry logic exists, retries up to its configured limit then gives up; does not crash |
| T007 | 5a – Wrong content (HTML instead of PDF) | HTTP stub returns 200 with `Content-Type: text/html` and a login-page body for a URL ending in `.pdf` | Attempt to download and process as a policy document         | Detects the mismatch between expected and actual type; reports document as invalid/suspect; does not try to parse HTML as a PDF |
| T008 | 5b – Corrupt PDF                         | HTTP stub returns 200 with `Content-Type: application/pdf` but bytes that fail the `%PDF` magic-byte check (or pass it but won't open in a PDF library) | Attempt to download and open/parse the file                  | Catches the parsing failure; records document as corrupt/unreadable rather than crashing or reporting success |

---

## Task 3 – Fixtures and Test Doubles

### 3.1 Fixtures as a source of truth

Repeatable scraper tests should use small, version-controlled fixtures whose
expected results are known in advance. Useful fixtures include a normal HTML
index page, an index page with changed markup, truncated HTML, a valid PDF, an
HTML error page returned from a `.pdf` URL, and a corrupt or incomplete PDF.
The expected links, status, content type, file signature and error outcome can
be recorded with each fixture. A local HTTP server can then serve the same
bytes and headers on every test run, so a result does not depend on changes to
the live website or the network.

### 3.2 Observations about request limits

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

### 3.3 Test doubles in Java

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

### 3.4 Demonstrated failure scenario

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
deliberately returns a well-formed HTTP response. The companion
`task3/BadPdfScenarioCheck.java` checks the status, content type and `%PDF-`
signature and prints `RESULT: FAILURE REPRODUCED`, making the outcome explicit.

This scenario is undesirable to reproduce on the live site because the team
does not control that server and should not replace a real policy document with
incorrect content. The local test double is safe, deterministic and can be run
repeatedly without sending test traffic to the production-like service.

---

## Task 4 – Adversarial Inputs

To find hidden assumptions, we wrote out the happy path (fetch, parse, extract links, resolve, filter, download, check) and asked at each step what is taken for granted and what input would break it. Each idea below gives the assumption challenged, the test input or environment, and what counts as failure and how it is detected.

### 4.1 Adversarial test ideas

| ID   | (a) Assumption challenged | (b) Test input / environment | (c) Failure and detection |
| ---- | ------------------------- | ----------------------------- | -------------------------- |
| A001 | File extensions are lowercase and end the URL path | Index page linking to `Policy.PDF`, `policy.pdf?download=1` and `policy.pdf#page=2`, all serving valid PDFs | Failure: some links are skipped. Detected when the number of documents found is lower than the number of links in the fixture |
| A002 | Every policy link ends in a file extension | A link like `/download?id=5` that returns a real PDF | Failure: the link is never found. Detected when it is missing from the scraper’s list of discovered links |
| A003 | Files are small enough to fit in memory | Local server returning a `.pdf` with a huge `Content-Length` (e.g. 2 GB) or an endless stream of bytes | Failure: out-of-memory error, hang or very long run. Detected by monitoring memory and runtime, or a test time limit |
| A004 | A request timeout also limits the whole download | Local server that sends headers immediately, then drips the file body at 1 byte per second | Failure: the run takes minutes or hangs even though a 10-second timeout is configured. Detected by total runtime far exceeding the timeout |
| A005 | The index URL responds directly from the same site | Local server that redirects the index page to another host, or redirects in a loop | Failure: scraper follows the redirect off-site or loops until the request limit. Detected by an unexpected host in the request log, or the request count hitting the cap |
| A006 | Links are well-formed and resolve against the page URL | Index with `<base href="/other/">`, `../x.pdf`, filenames with spaces or non-ASCII characters, and `mailto:` or `javascript:` links | Failure: wrong URLs requested or an exception on a bad link. Detected by 404s, URLs in the log that don’t match the expected ones, or an uncaught exception |
| A007 | Real policy documents are on the same host as the index | Index linking to PDFs on a second local server (e.g. `localhost:3001`) standing in for a CDN or document store | Failure: valid documents are skipped as off-site. Detected when the scraper finds 0 documents even though the fixture links to several |
| A008 | The server treats the scraper like any other visitor | Local server that returns 403 or a CAPTCHA page when the `User-Agent` is not a browser string | Failure: scraper reports "no policy links" or a generic error with no hint of the cause. Detected by comparing the output with the same fixture served to a browser-style request |
