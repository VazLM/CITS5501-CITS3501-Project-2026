## Task 4 – Adversarial Inputs

To find hidden assumptions, we wrote out the happy path (fetch, parse, extract links, resolve, filter, download, check) and asked at each step what is taken for granted and what input would break it. Each idea below gives the assumption challenged, the test input or environment, and what counts as failure and how it is detected.

### Adversarial test ideas

| ID   | (a) Assumption challenged | (b) Test input / environment | (c) Failure and detection |
| ---- | ------------------------- | ---------------------------- | ------------------------- |
| A001 | File extensions are lowercase and end the URL path | Index page linking to `Policy.PDF`, `policy.pdf?download=1` and `policy.pdf#page=2`, all serving valid PDFs | Failure: some links are skipped. Detected when the number of documents found is lower than the number of links in the fixture |
| A002 | Every policy link ends in a file extension | A link like `/download?id=5` that returns a real PDF | Failure: the link is never found. Detected when it is missing from the scraper’s list of discovered links |
| A003 | Files are small enough to fit in memory | Local server returning a `.pdf` with a huge `Content-Length` (e.g. 2 GB) or an endless stream of bytes | Failure: out-of-memory error, hang or very long run. Detected by monitoring memory and runtime, or a test time limit |
| A004 | A request timeout also limits the whole download | Local server that sends headers immediately, then drips the file body at 1 byte per second | Failure: the run takes minutes or hangs even though a 10-second timeout is configured. Detected by total runtime far exceeding the timeout |
| A005 | The index URL responds directly from the same site | Local server that redirects the index page to another host, or redirects in a loop | Failure: scraper follows the redirect off-site or loops until the request limit. Detected by an unexpected host in the request log, or the request count hitting the cap |
| A006 | Links are well-formed and resolve against the page URL | Index with `<base href="/other/">`, `../x.pdf`, filenames with spaces or non-ASCII characters, and `mailto:` or `javascript:` links | Failure: wrong URLs requested or an exception on a bad link. Detected by 404s, URLs in the log that don’t match the expected ones, or an uncaught exception |
| A007 | Real policy documents are on the same host as the index | Index linking to PDFs on a second local server (e.g. `localhost:3001`) standing in for a CDN or document store | Failure: valid documents are skipped as off-site. Detected when the scraper finds 0 documents even though the fixture links to several |
| A008 | The server treats the scraper like any other visitor | Local server that returns 403 or a CAPTCHA page when the `User-Agent` is not a browser string | Failure: scraper reports "no policy links" or a generic error with no hint of the cause. Detected by comparing the output with the same fixture served to a browser-style request |


