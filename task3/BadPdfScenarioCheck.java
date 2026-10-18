import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

/**
 * Checks that the Task 3 test double reproduces the intended failure scenario.
 *
 * <p>This is deliberately separate from the Task 1 prototype. The prototype
 * reports HTTP metadata, while this checker states the expected document
 * validation result explicitly.</p>
 */
public final class BadPdfScenarioCheck {

    private static final String DEFAULT_URL = "http://localhost:8000/fake-policy.pdf";
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private BadPdfScenarioCheck() {
        // This class only contains a command-line entry point.
    }

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : DEFAULT_URL;
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();

        HttpResponse<byte[]> response = client.send(
            request, HttpResponse.BodyHandlers.ofByteArray());

        String contentType = response.headers()
            .firstValue("Content-Type")
            .orElse("not provided");
        boolean pdfUrl = response.uri().getPath().toLowerCase().endsWith(".pdf");
        boolean successfulHttp = response.statusCode() >= 200 && response.statusCode() < 300;
        boolean pdfContentType = contentType.toLowerCase().startsWith("application/pdf");
        boolean pdfSignature = startsWith(response.body(), PDF_SIGNATURE);

        System.out.println("Task 3 failure-scenario check");
        System.out.println("  URL suggests PDF: " + pdfUrl);
        System.out.println("  HTTP status: " + response.statusCode());
        System.out.println("  Content-Type: " + contentType);
        System.out.println("  Starts with %PDF-: " + pdfSignature);
        System.out.println();

        if (pdfUrl && successfulHttp && (!pdfContentType || !pdfSignature)) {
            System.out.println("RESULT: FAILURE REPRODUCED");
            System.out.println("Expected: a valid PDF response.");
            System.out.println(
                "Observed: the .pdf URL returned HTTP 200, but the content is not a PDF.");
            return;
        }

        System.out.println("RESULT: SCENARIO NOT REPRODUCED");
        throw new AssertionError("The response did not match the intended bad-PDF fixture");
    }

    /** Return true when {@code value} begins with all bytes from {@code prefix}. */
    private static boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length
            && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
    }
}
