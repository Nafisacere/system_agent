package agent;

import java.io.*;
import java.net.*;

// this class handles GET requests from the browser
// GET /           --> sends back the dashboard HTML page
// GET /data       --> sends back the latest metrics as JSON
public class DashboardHandler {

    private final MetricsStore store;
    private final String       htmlFolder;

    public DashboardHandler(MetricsStore store, String htmlFolder) {
        this.store      = store;
        this.htmlFolder = htmlFolder;
    }

    // called by FakeServer for every GET request
    // path is already parsed by FakeServer (e.g. "/" or "/data")
    public void handle(Socket client, String path) {
        try {
            // set a timeout so we never hang waiting for the browser to send headers
            client.setSoTimeout(2000);

            InputStream  rawIn = client.getInputStream();
            OutputStream out   = client.getOutputStream();

            // drain remaining headers (we already read the first line in FakeServer)
            // use a timeout so if the browser stops sending we don't get stuck
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(rawIn));
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    // just discard headers
                }
            } catch (Exception ignored) {
                // timeout or no more headers — that's fine, just continue
            }

            // now send the response
            if (path.equals("/data")) {
                sendJson(out);
            } else {
                sendHtml(out);
            }

            out.flush();

        } catch (Exception e) {
            System.out.println("[Server] Dashboard error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    // sends the JSON data that the dashboard polls every 1.5 seconds
    private void sendJson(OutputStream out) throws Exception {
        byte[] body = store.toJson().getBytes("UTF-8");
        writeResponse(out, "200 OK", "application/json", body);
    }

    // reads dashboard.html from disk and sends it to the browser
    private void sendHtml(OutputStream out) throws Exception {
        File htmlFile = new File(htmlFolder, "dashboard.html");

        if (!htmlFile.exists()) {
            String msg = "dashboard.html not found. Expected at: " + htmlFile.getAbsolutePath();
            writeResponse(out, "404 Not Found", "text/plain", msg.getBytes("UTF-8"));
            System.out.println("[Server] WARNING: " + msg);
            return;
        }

        FileInputStream fis  = new FileInputStream(htmlFile);
        byte[]          body = fis.readAllBytes();
        fis.close();

        writeResponse(out, "200 OK", "text/html; charset=UTF-8", body);
    }

    // writes a complete HTTP response
    private void writeResponse(OutputStream out, String status, String contentType, byte[] body) throws Exception {
        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(headers.getBytes("UTF-8"));
        out.write(body);
    }
}
