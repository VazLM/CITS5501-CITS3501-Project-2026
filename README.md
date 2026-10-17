# Policy scraper prototype -- Java starter

Starter code for Task 1 of the CITS5501 Project, Phase 2.

`src/main/java/scraper/Scraper.java` fetches the policy index page, extracts
its links and prints them. Your job is to fill in the parts marked `TODO`:
decide which links look like policy documents, download 3-5 of them, and print
their metadata.

## Requirements

- A JDK, version 17 or later (e.g. from <https://adoptium.net/>).

That's all that's needed. The started code uses Maven to manage dependencies.
The included Maven wrapper (`mvnw` / `mvnw.cmd`) downloads Maven
itself the first time you run it, and Maven downloads the one dependency
([jsoup](https://jsoup.org/), for HTML parsing). HTTP requests use the JDK's
built-in `java.net.http.HttpClient`.

It should be possible to develop on any of Linux, macOS or Windows.
(The marking environment is Linux, as previously specified - so it is a good idea
if someone can run the code on Linux before submitting - but in general, Java
code should work equally well on any platform.)

## Running

Unzip the starter code files somewhere, and from that directory, run the Maven wrapper.
That will compile the code and run the scraper.

On macOS/Linux:

```bash
./mvnw -q compile exec:java
```

On Windows, use `mvnw.cmd` in place of `./mvnw`:

```
mvnw.cmd -q compile exec:java
```

To start from a different page (for instance, a server you are running
locally for testing), pass it as an argument:

```bash
./mvnw -q compile exec:java -Dexec.args="http://localhost:3000/"
```

(Drop the `-q` if you want to see what Maven is doing.)

If you just want to compile, without running, you can use

```
mvnw.cmd compile
```

## Using an IDE

IntelliJ, Eclipse and VS Code (with the Java extensions) can all open this
directory as a Maven project. You can then run `Scraper.main()` directly from
the IDE.

## Request limits

All requests should go through the `fetch()` method, which:

- sets 10-second connect and request timeouts,
- identifies the program with a User-Agent header,
- waits 1 second between requests, and
- refuses to make more than 20 requests per run.

It's recommended you keep these limits when running against the live site.

## Updating this README

When you submit, update this README -- delete the contents, and replace it with
your own content, so it describes *your* prototype: how to
run it, and anything a marker should know (e.g. extra command-line options or
dependencies you added).


