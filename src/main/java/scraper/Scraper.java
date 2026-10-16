package scraper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * CITS5501 Project Phase 2 -- starter code for the Task 1 minimal prototype.
 *
 * What this starter already does:
 * 
 * - reads an optional start URL from the command line (defaults to the
 *   policy index page),
 * - fetches that page via {@link #fetch}, which sets timeouts and a
 *   User-Agent, waits briefly between requests, and refuses to make more
 *   than {@link #MAX_REQUESTS} requests,
 * - extracts every {@code <a href="...">} link on the page and makes it
 *   absolute,
 * - prints the links it found.
 *
 * What you need to write (look for "TODO"):
 *
 * - decide which links look like policy documents (see isPolicyLink) 
 * - download one document and print its metadata (see describeDownload) 
 * - the loop at the end of main, that picks 3-5 distinct
 *   documents and calls describeDownload() on each.
 *
 * Run with (see README.md for details):
 *
 * <pre>
 *   ./mvnw -q compile exec:java
 *   # OR against a locally running site
 *   ./mvnw -q compile exec:java -Dexec.args="http://localhost:3000/"
 * </pre>
 */
public class Scraper {

    public static final String START_URL =
        "https://uwa-policy.cits5501.arranstewart.io/policy/";

    // Politeness and safety limits. Please don't raise these when running
    // against the live site.
    public static final int MAX_REQUESTS = 20;                     // per run (index page + downloads)
    public static final Duration TIMEOUT = Duration.ofSeconds(10); // connect, and per request
    public static final Duration DELAY = Duration.ofSeconds(1);    // pause between requests

    public static final String USER_AGENT =
        "CITS5501-student-scraper/0.1 (university course project)";

    /** Thrown when a run tries to make more than MAX_REQUESTS requests. */
    public static class RequestLimitExceededException extends RuntimeException {
        public RequestLimitExceededException(String message) {
            super(message);
        }
    }

    private final HttpClient client;
    private int requestCount = 0;

    public Scraper() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /** Number of requests made so far by this scraper. */
    public int getRequestCount() {
        return requestCount;
    }

    /**
     * Make a GET request, enforcing the request cap, a timeout and a delay.
     *
     * Use this for every request your prototype makes, rather
     * than calling the HttpClient directly. The body handler says how
     * the response body should be read, e.g. {@code BodyHandlers.ofString()}
     * for HTML, or {@code BodyHandlers.ofByteArray()} for a downloaded file.
     *
     * Note that this does NOT throw an exception for 4xx/5xx status
     * codes -- check {@code response.statusCode()} yourself.
     *
     * @throws RequestLimitExceededException if MAX_REQUESTS have already been made
     */
    public <T> HttpResponse<T> fetch(String url, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        if (requestCount >= MAX_REQUESTS) {
            throw new RequestLimitExceededException(
                "request limit of " + MAX_REQUESTS + " reached; not fetching " + url);
        }
        if (requestCount > 0) {
            Thread.sleep(DELAY.toMillis());
        }
        requestCount++;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        return client.send(request, bodyHandler);
    }

    /**
     * Return the absolute URLs of every {@code <a href="...">} link in
     * {@code html}, in the order they appear. Relative links are resolved
     * against {@code baseUrl}. Duplicates are not removed.
     */
    public static List<String> extractLinks(String html, String baseUrl) {
        Document doc = Jsoup.parse(html, baseUrl);
        List<String> links = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            links.add(a.absUrl("href"));   // "" if the href can't be resolved
        }
        return links;
    }

    /**
     * Return true if {@code url} looks like it leads to a downloadable policy
     * document (e.g. a PDF or office file), otherwise false.
     *
     * <p>TODO: implement this. Filtering by file extension is acceptable for
     * the prototype -- but note its limitations in your report.
     */
    public static boolean isPolicyLink(String url) {
        return false;
    }

    /**
     * Download the document at {@code url} and print its metadata: for
     * example the URL, HTTP status, declared content type, downloaded size,
     * and (optionally) the first few bytes of the response body.
     *
     * <p>The downloaded data does not need to be saved to disk.
     *
     * <p>TODO: implement this, using {@link #fetch} to make the request.
     */
    public void describeDownload(String url) throws IOException, InterruptedException {
        throw new UnsupportedOperationException("describeDownload() not written yet");
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String startUrl = args.length > 0 ? args[0] : START_URL;
        Scraper scraper = new Scraper();

        System.out.println("Fetching index page: " + startUrl);
        HttpResponse<String> response =
            scraper.fetch(startUrl, HttpResponse.BodyHandlers.ofString());
        System.out.println("  status: " + response.statusCode()
            + ", content type: "
            + response.headers().firstValue("Content-Type").orElse(null));

        // response.uri() is the final URL, after any redirects.
        List<String> links = extractLinks(response.body(), response.uri().toString());
        System.out.println("Found " + links.size() + " links:");
        for (String link : links) {
            System.out.println("  " + link);
        }

        List<String> policyLinks = new ArrayList<>();
        for (String link : links) {
            if (isPolicyLink(link)) {
                policyLinks.add(link);
            }
        }
        System.out.println(policyLinks.size() + " of these look like policy documents.");

        // TODO: choose 3-5 *distinct* policy links and call describeDownload()
        // on each. Keep within MAX_REQUESTS (the index page already used one).

        System.out.println("Requests made: " + scraper.getRequestCount()
            + " (limit " + MAX_REQUESTS + ")");
    }
}
