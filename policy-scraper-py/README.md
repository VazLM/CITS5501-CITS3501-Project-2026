# Policy document scraper prototype

This is the Python prototype for Task 1 of the CITS5501 Project, Phase 2.

`scraper.py` fetches the supplied policy index page, finds links that look like
PDF or Office documents, removes duplicate links, and downloads up to five
documents. For each download it prints the URL, HTTP status, declared content
type, number of downloaded bytes, and the first 16 bytes of the response.

## Requirements

- [uv](https://docs.astral.sh/uv/) (recommended), or plain `pip`
- Python 3.11 or later (which can be installed using uv - see below)

Dependencies (`requests`, `beautifulsoup4`) are included in `requirements.txt`,
and the sections below show how they can be installed.

It should be possible to develop on any of Linux, macOS or Windows.
(The marking environment is Linux, as previously specified - so it is a good idea
if someone can run the code on Linux before submitting - but in general, Python
code should work equally well on any platform.)

## Recommended approach, using uv

The only thing you *need* to install is [uv](https://docs.astral.sh/uv/); it runs on Windows, Linux, and
macOS, and will fetch a working version of Python and the other project
dependencies for you. You are welcome to install Python in some other way if you wish, but
we provide instructions that use uv.

A recommended way to manage requirements is as follows.

First, install uv by following the instructions at
<https://docs.astral.sh/uv/getting-started/installation/>.

Then, unzip this zip file to some location on your computer.
Included in the zip file is a `.python-version` file, which tells uv to use Python 3.12.
If you don't have Python already installed, then uv will install it the first time you run the following
command:

```bash
uv run --with-requirements requirements.txt scraper.py
```

uv downloads the dependencies _once_, and puts them into a cache, so that later
runs start quickly. To start from a different page (for instance, a server you
are running locally for testing):

```bash
uv run --with-requirements requirements.txt scraper.py http://localhost:3000/
```

## Using uv and an explicit virtual environment

You may be familiar with Python *virtual environments*. As an alternative to the "quick and
simple" approach above, it's also possible to create a virtual environment in the project directory:

```bash
uv venv
uv pip install -r requirements.txt
uv run scraper.py
```

This creates a directory `.venv` containing all project requirements. (Note that in general,
a `.venv` directory is tied to the exact location it's been created -- if you try to move or
rename your project directory, it may stop working.)

## Running without uv

If you already have a version of Python you prefer to use, the following commands
should work to install dependencies and run the main script.

```bash
python3 -m venv .venv
source .venv/bin/activate        # on Windows: .venv\Scripts\activate
pip install -r requirements.txt
python scraper.py
```

## Request limits

All requests should go through the `fetch()` function, which:

- sets a 10-second timeout (the `requests` library has no timeout by default),
- identifies the program with a User-Agent header,
- waits 1 second between requests, and
- stops the program after 20 requests.

It's recommended you keep these limits when running against the live site.

## Prototype behaviour and limitations

- Only links on the same host as the index page are downloaded.
- A link is treated as a policy document when its URL path ends with a common
  document extension such as `.pdf`, `.docx`, or `.xlsx`. Matching ignores
  letter case and URL query strings.
- Duplicate links are removed while keeping their original order.
- At most five documents are downloaded and all response data is discarded
  after its metadata is printed.

This is deliberately a minimal prototype. Extension-based detection can miss a
document served from a URL with no extension, and it can accept a misleading URL
whose contents are not a real document. A successful HTTP status and a familiar
file signature also do not prove that the whole file is valid or can be opened.
