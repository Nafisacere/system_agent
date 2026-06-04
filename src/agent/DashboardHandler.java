package agent;

import java.io.*;
import java.net.*;

public class DashboardHandler {

    private final MetricsStore store;
    private final String       htmlFolder;

    public DashboardHandler(MetricsStore store, String htmlFolder) {
        this.store      = store;
        this.htmlFolder = htmlFolder;
    }

    public void handle(Socket client, String path) {
        try {
            client.setSoTimeout(2000);

            InputStream  rawIn = client.getInputStream();
            OutputStream out   = client.getOutputStream();

            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(rawIn));
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {}
            } catch (Exception ignored) {}

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

    private void sendJson(OutputStream out) throws Exception {
        byte[] body = store.toJson().getBytes("UTF-8");
        writeResponse(out, "200 OK", "application/json", body);
    }

    private void sendHtml(OutputStream out) throws Exception {
        File htmlFile = new File(htmlFolder, "dashboard.html");

        if (!htmlFile.exists()) {
            String msg = "dashboard.html not found at: " + htmlFile.getAbsolutePath();
            writeResponse(out, "404 Not Found", "text/plain", msg.getBytes("UTF-8"));
            System.out.println("[Server] WARNING: " + msg);
            return;
        }

        FileInputStream fis  = new FileInputStream(htmlFile);
        byte[]          body = fis.readAllBytes();
        fis.close();

        writeResponse(out, "200 OK", "text/html; charset=UTF-8", body);
    }

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
