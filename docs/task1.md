## Task 1 – Prototype

### What it does

`Scraper.java` fetches the course's policy index page, extracts every `<a href>` link on it using jsoup, and keeps only the links that (a) resolve to the same host as the index page and (b) end in one of a fixed set of document extensions (`.pdf`, `.doc`, `.docx`, `.xls`, `.xlsx`, `.ppt`, `.pptx`, `.odt`, `.ods`, `.odp`, `.rtf`). After deduplicating, it attempts to download the first five distinct matches and prints, for each: HTTP status, whether that status counts as success, content type, downloaded size, and the first 16 bytes of the body. A single client enforces a hard cap of 20 requests for the whole run, a 10-second timeout per request, a 1-second delay between requests, and a custom `User-Agent` identifying it as a student project.

### Limitations

1. **Extension-based filtering only.** `isPolicyLink` decides whether a link is a policy document purely from the URL's file extension, it never checks the HTTP response's `Content-Type` header or the downloaded bytes' actual file signature. A link ending in `.pdf` that actually serves an HTML error page (or vice versa) is accepted or rejected on the strength of its name alone. This is exactly the gap Task 2/3's "malformed document" scenario (T007/T008) is designed to probe.
2. **No per-document fault isolation.** `main` calls `describeDownload` for each selected link with no `try/catch` around the loop. A timeout, a connection failure, or any `IOException` on one document propagates straight out of `main` and ends the whole run, rather than being recorded as a failure for that one document while the rest continue. There is also no retry logic to distinguish a transient 5xx from a permanent 4xx. This is the central behaviour Task 2's timeout and status-code scenarios (T003–T006) test for.
3. **No signal when the page structure changes.** Link discovery assumes the index page's existing markup (plain `<a href>` tags) stays stable. If the real site changed how it marks up policy links, the scraper would simply find zero matching links and print "0 of these look like policy documents", a normal-looking exit with no indication that something broke, which is indistinguishable from a page that legitimately has no policy documents (Task 2's T001).

These are intentional simplifications, not oversights, the brief is explicit that the prototype does not need to be robust. Tasks 2–4 reason about what a fully robust version of this same design would need to do differently.

### Assumptions

- Policy documents are always linked from the same host as the index page; the prototype explicitly discards cross-host links (`sameHost`) before even checking the extension, so it would miss documents served from, say, a separate CDN or document store (see Task 4, A007).
- A file extension is a reasonably reliable signal of document type. The team's own probe against the real site (Task 3, §3.2) found it enforces `X-Ratelimit-Limit: 3, tighter than the 20-request budget the prototype assumes it can spend, so this assumption about the service's tolerance is already known not to fully hold; the prototype was deliberately left unchanged in response to this, consistent with the brief's scope for Task 1.
- The index page lists documents directly as `<a href>` elements reachable from a single fetch, with no pagination or JavaScript-rendered content to deal with.
