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
            } else if (path.startsWith("/history")) {
                sendHistory(out, path);
            } else if (path.startsWith("/stats")) {
                sendStats(out, path);
            } else if (path.startsWith("/uptime")) {
                sendUptime(out);
            } else if (path.equals("/login")) {
                sendFile(out, "login.html");
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

    // /history?machine=NAFISA&range=3600000&page=0
    // range = milliseconds (1h=3600000, 6h=21600000, 24h=86400000, 0=all)
    private void sendHistory(OutputStream out, String path) throws Exception {
        String machine = getParam(path, "machine", "");
        long   range   = Long.parseLong(getParam(path, "range", "0"));
        int    page    = Integer.parseInt(getParam(path, "page", "0"));
        int    size    = Integer.parseInt(getParam(path, "size", "50"));

        String json = store.historyJson(machine, range, page, size);
        writeResponse(out, "200 OK", "application/json", json.getBytes("UTF-8"));
    }

    // /stats?machine=NAFISA&range=3600000
    private void sendStats(OutputStream out, String path) throws Exception {
        String machine = getParam(path, "machine", "");
        long   range   = Long.parseLong(getParam(path, "range", "0"));

        String json = store.statsJson(machine, range);
        writeResponse(out, "200 OK", "application/json", json.getBytes("UTF-8"));
    }

    // /uptime  — returns uptime per machine
    private void sendUptime(OutputStream out) throws Exception {
        String json = store.uptimeJson();
        writeResponse(out, "200 OK", "application/json", json.getBytes("UTF-8"));
    }

    private void sendJson(OutputStream out) throws Exception {
        byte[] body = store.toJson().getBytes("UTF-8");
        writeResponse(out, "200 OK", "application/json", body);
    }

    private void sendHtml(OutputStream out) throws Exception {
        sendFile(out, "dashboard.html");
    }

    private void sendFile(OutputStream out, String filename) throws Exception {
        File file = new File(htmlFolder, filename);
        if (!file.exists()) {
            String msg = filename + " not found at: " + file.getAbsolutePath();
            writeResponse(out, "404 Not Found", "text/plain", msg.getBytes("UTF-8"));
            return;
        }
        FileInputStream fis  = new FileInputStream(file);
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

    // simple query string parser: getParam("?machine=NAFISA&range=3600", "machine", "") -> "NAFISA"
    private String getParam(String path, String key, String fallback) {
        int q = path.indexOf('?');
        if (q == -1) return fallback;
        String query = path.substring(q + 1);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return fallback;
    }
}
