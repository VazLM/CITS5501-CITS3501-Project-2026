import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A small local test double for Phase 2 Task 3.
 *
 * <p>The index page contains a link whose path ends in {@code .pdf}, but the
 * download endpoint deliberately returns an HTML page with HTTP status 200.
 * This reproduces the "PDF link returns HTML" failure scenario without
 * changing or repeatedly exercising the live policy website.</p>
 */
public final class BadPdfServer {

    private static final int PORT = 8000;

    private BadPdfServer() {
        // This class only contains a command-line entry point.
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);

        // A minimal fixture page that the scraper can use as its start page.
        server.createContext("/", exchange -> send(
            exchange,
            200,
            "text/html; charset=utf-8",
            """
                <!doctype html>
                <html lang="en">
                  <head><meta charset="utf-8"><title>Policy fixtures</title></head>
                  <body>
                    <h1>Policy fixtures</h1>
                    <a href="/fake-policy.pdf">Download the example policy</a>
                  </body>
                </html>
                """
        ));

        // The URL looks like a PDF, but the response is deliberately HTML.
        server.createContext("/fake-policy.pdf", exchange -> send(
            exchange,
            200,
            "text/html; charset=utf-8",
            """
                <!doctype html>
                <html lang="en">
                  <head><meta charset="utf-8"><title>Not a PDF</title></head>
                  <body><p>This is an HTML error page, not a policy PDF.</p></body>
                </html>
                """
        ));

        server.start();
        System.out.println("Task 3 test double running at http://localhost:" + PORT + "/");
        System.out.println("Press Ctrl+C to stop it.");
    }

    /** Send a deterministic response and close the exchange cleanly. */
    private static void send(
            HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
