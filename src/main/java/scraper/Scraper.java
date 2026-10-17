package scraper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * CITS5501 Project Phase 2 -- Task 1 minimal prototype.
 *
 * What this prototype does:
 *
 * - reads an optional start URL from the command line (defaults to the
 *   policy index page),
 * - fetches that page via {@link #fetch}, which sets timeouts and a
 *   User-Agent, waits briefly between requests, and refuses to make more
 *   than {@link #MAX_REQUESTS} requests,
 * - extracts every {@code <a href="...">} link on the page and makes it
 *   absolute,
 * - identifies same-site links that look like policy documents,
 * - removes duplicate document links,
 * - downloads up to five documents and prints useful response metadata.
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

    // The prototype identifies documents by their file extension. This is
    // simple and suitable for Task 1, although the report should mention
    // that links with no extension may be missed.
    public static final List<String> POLICY_EXTENSIONS = List.of(
        ".pdf", ".doc", ".docx", ".xls", ".xlsx",
        ".ppt", ".pptx", ".odt", ".ods", ".odp", ".rtf"
    );

    // The specification asks the prototype to attempt 3-5 distinct downloads.
    public static final int DOCUMENTS_TO_DOWNLOAD = 5;

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
     * <p>Filtering by file extension is acceptable for the prototype -- but
     * note its limitations in your report. The query string and fragment
     * are ignored, so both {@code policy.pdf} and {@code policy.pdf?download=1}
     * are accepted.
     */
    public static boolean isPolicyLink(String url) {
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (path == null) {
            return false;
        }
        String pathLower = path.toLowerCase();
        for (String extension : POLICY_EXTENSIONS) {
            if (pathLower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if {@code url} has the same host as {@code host}. Used to
     * exclude links that point to other websites -- the assignment does not
     * require following links off-site. Returns false if either host can't
     * be determined.
     */
    private static boolean sameHost(String url, String host) {
        if (host == null) {
            return false;
        }
        try {
            return host.equals(URI.create(url).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Download the document at {@code url} and print its metadata: for
     * example the URL, HTTP status, declared content type, downloaded size,
     * and (optionally) the first few bytes of the response body.
     *
     * <p>The downloaded data does not need to be saved to disk.
     */
    public void describeDownload(String url) throws IOException, InterruptedException {
        // response.body() gives the raw bytes, which is what we need for the
        // file size and file signature. fetch() still enforces the timeout,
        // delay and request limit.
        HttpResponse<byte[]> response = fetch(url, HttpResponse.BodyHandlers.ofByteArray());
        byte[] downloadedData = response.body();

        // A 2xx response means the HTTP request succeeded. It does not prove
        // that the returned bytes form a valid PDF or Office document; that
        // is one of the limitations discussed in the report.
        boolean httpSuccess = response.statusCode() >= 200 && response.statusCode() < 300;

        System.out.println();
        System.out.println("Download result:");
        System.out.println("  URL: " + url);
        System.out.println("  successful HTTP response: " + (httpSuccess ? "yes" : "no"));
        System.out.println("  HTTP status: " + response.statusCode());
        System.out.println("  content type: "
            + response.headers().firstValue("Content-Type").orElse("not provided"));
        System.out.println("  downloaded size: " + downloadedData.length + " bytes");

        int previewLength = Math.min(16, downloadedData.length);
        String preview = new String(downloadedData, 0, previewLength, StandardCharsets.ISO_8859_1);
        System.out.println("  first " + previewLength + " bytes: " + preview);
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

        // Only keep documents from the same host as the index page. The
        // assignment does not require following links to other websites.
        String indexHost = response.uri().getHost();
        List<String> policyLinks = new ArrayList<>();
        for (String link : links) {
            if (sameHost(link, indexHost) && isPolicyLink(link)) {
                policyLinks.add(link);
            }
        }
        System.out.println(policyLinks.size() + " of these look like policy documents.");

        // LinkedHashSet removes duplicates while keeping the links in the
        // same order as they appeared on the page; subList then keeps at
        // most DOCUMENTS_TO_DOWNLOAD of them.
        List<String> distinctPolicyLinks = new ArrayList<>(new LinkedHashSet<>(policyLinks));
        List<String> selectedLinks = distinctPolicyLinks.subList(
            0, Math.min(DOCUMENTS_TO_DOWNLOAD, distinctPolicyLinks.size()));

        System.out.println("Attempting to download " + selectedLinks.size() + " distinct documents.");
        for (String link : selectedLinks) {
            scraper.describeDownload(link);
        }

        System.out.println("Requests made: " + scraper.getRequestCount()
            + " (limit " + MAX_REQUESTS + ")");
    }
}
