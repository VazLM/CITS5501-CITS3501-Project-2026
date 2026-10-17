#!/usr/bin/env python3
"""
CITS5501 Project Phase 2 -- starter code for the Task 1 minimal prototype.

What this prototype does:

- reads an optional start URL from the command line (defaults to the
  policy index page),
- fetches that page via `fetch()`, which sets a timeout and a User-Agent,
  waits briefly between requests, and stops the program if it would exceed
  MAX_REQUESTS,
- extracts every <a href="..."> link on the page and makes it absolute,
- identifies same-site links that look like policy documents,
- removes duplicate document links,
- downloads up to five documents and prints useful response metadata.

Run with:

    uv run --with-requirements requirements.txt scraper.py
    uv run --with-requirements requirements.txt scraper.py http://localhost:3000/

(See README.md for other ways to run it.)
"""

import argparse
import sys
import time
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

START_URL = "https://uwa-policy.cits5501.arranstewart.io/policy/"

# Politeness and safety limits. Please don't raise these when running
# against the live site.
MAX_REQUESTS = 20       # total requests per run (index page + downloads)
TIMEOUT_SECONDS = 10    # per request; `requests` has NO timeout by default
DELAY_SECONDS = 1.0     # pause between consecutive requests

# You may want to add your team number here
USER_AGENT = "CITS5501-student-scraper/0.1 (university course project)"

# The prototype identifies documents by their file extension.  This is simple
# and suitable for Task 1, although the report should mention that links with no
# extension may be missed.
POLICY_EXTENSIONS = {
    ".pdf",
    ".doc",
    ".docx",
    ".xls",
    ".xlsx",
    ".ppt",
    ".pptx",
    ".odt",
    ".ods",
    ".odp",
    ".rtf",
}

# The specification asks the prototype to attempt 3-5 distinct downloads.
DOCUMENTS_TO_DOWNLOAD = 5


class RequestLimitExceeded(Exception):
    """Raised when a run tries to make more than MAX_REQUESTS requests."""

# Keep track of how many requests we have made.
_request_count = 0


def fetch(session, url, **kwargs):
    """
    Make a GET request, enforcing the request cap, a timeout and a delay.

    Use this for every request your prototype makes, rather than calling
    `session.get()` directly. Any extra keyword arguments (e.g. `stream=True`)
    are passed through to `session.get()`.

    Returns a `requests.Response`. Note that this does NOT raise an exception
    for 4xx/5xx status codes -- check `response.status_code` yourself.
    """

    global _request_count
    if _request_count >= MAX_REQUESTS:
        raise RequestLimitExceeded(
            f"request limit of {MAX_REQUESTS} reached; not fetching {url}"
        )
    if _request_count > 0:
        time.sleep(DELAY_SECONDS)
    _request_count += 1

    kwargs.setdefault("timeout", TIMEOUT_SECONDS)
    return session.get(url, **kwargs)


def extract_links(html, base_url):
    """
    Return a list of absolute URLs for every <a href="..."> in `html`,
    in the order they appear. Relative links are resolved against `base_url`.
    Duplicates are NOT removed.
    """
    soup = BeautifulSoup(html, "html.parser")
    return [urljoin(base_url, a["href"]) for a in soup.find_all("a", href=True)]


def is_policy_link(url):
    """
    Return True if `url` looks like it leads to a downloadable policy
    document (e.g. a PDF or office file), otherwise False.

    The query string and fragment are ignored.  For example, both
    ``policy.pdf`` and ``policy.pdf?download=1`` are accepted.
    """
    parsed_url = urlparse(url)
    path_lower = parsed_url.path.lower()
    return any(path_lower.endswith(extension) for extension in POLICY_EXTENSIONS)


def describe_download(session, url):
    """
    Download the document at `url` and print its metadata: for example the
    URL, HTTP status, declared content type, downloaded size, and
    (optionally) the first few bytes of the response body.

    The downloaded data is kept in memory only and is discarded when this
    function returns.
    """
    # response.content gives the raw bytes, which is what we need for the file
    # size and file signature.  All network requests go through fetch() so the
    # timeout, delay and request limit are still enforced.
    response = fetch(session, url)
    downloaded_data = response.content

    # A 2xx response means the HTTP request succeeded.  It does not prove that
    # the returned bytes form a valid PDF or Office document; that is one of the
    # limitations discussed in the report.
    http_success = 200 <= response.status_code < 300

    print("\nDownload result:")
    print(f"  URL: {url}")
    print(f"  successful HTTP response: {'yes' if http_success else 'no'}")
    print(f"  HTTP status: {response.status_code}")
    print(f"  content type: {response.headers.get('Content-Type', 'not provided')}")
    print(f"  downloaded size: {len(downloaded_data)} bytes")
    print(f"  first 16 bytes: {downloaded_data[:16]!r}")


def main():
    """
    main routine
    """

    parser = argparse.ArgumentParser(
        description="Minimal policy-document scraper prototype (CITS5501)."
    )
    parser.add_argument(
        "start_url",
        nargs="?",
        default=START_URL,
        help=f"index page to start from (default: {START_URL})",
    )
    args = parser.parse_args()

    session = requests.Session()
    session.headers["User-Agent"] = USER_AGENT

    print(f"Fetching index page: {args.start_url}")
    response = fetch(session, args.start_url)
    print(f"  status: {response.status_code}, "
          f"content type: {response.headers.get('Content-Type')}")

    links = extract_links(response.text, response.url)
    print(f"Found {len(links)} links:")
    for link in links:
        print(f"  {link}")

    # Only keep documents from the same host as the index page.  The assignment
    # does not require following links to other websites.
    index_host = urlparse(response.url).hostname
    policy_links = [
        link
        for link in links
        if urlparse(link).hostname == index_host and is_policy_link(link)
    ]
    print(f"{len(policy_links)} of these look like policy documents.")

    # dict.fromkeys() removes duplicates while keeping the links in the same
    # order as they appeared on the page.  Slicing selects at most five files.
    distinct_policy_links = list(dict.fromkeys(policy_links))
    selected_links = distinct_policy_links[:DOCUMENTS_TO_DOWNLOAD]

    print(f"Attempting to download {len(selected_links)} distinct documents.")
    for link in selected_links:
        describe_download(session, link)

    print(f"Requests made: {_request_count} (limit {MAX_REQUESTS})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
