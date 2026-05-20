package agent;

import java.io.*;
import java.net.*;

// this is the server that agents connect to
// it now also serves a live web dashboard at http://localhost:9000
public class FakeServer {

    private static final int PORT = 9000;

    // put dashboard.html in the same folder as your .java files
    private static final String HTML_FOLDER = "src/agent";

    public static void main(String[] args) throws Exception {
        MetricsStore     store = new MetricsStore();
        DashboardHandler dash  = new DashboardHandler(store, HTML_FOLDER);

        ServerSocket server = new ServerSocket(PORT);

        System.out.println("MONITORING SERVER");
        System.out.println("Agents send data to : http://localhost:" + PORT + "/metrics");
        System.out.println("Open dashboard at   : http://localhost:" + PORT + "/");
        System.out.println();

        while (true) {
            Socket client = server.accept();

            // handle each connection in a new thread
            // this is needed so the server can handle the browser and the agent at the same time
            new Thread(() -> handleClient(client, store, dash)).start();
        }
    }

    // reads the first line of the HTTP request and decides what to do
    private static void handleClient(Socket client, MetricsStore store, DashboardHandler dash) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            // first line looks like: "POST /metrics HTTP/1.1" or "GET / HTTP/1.1"
            String firstLine = in.readLine();
            if (firstLine == null) { client.close(); return; }

            String method = firstLine.split(" ")[0];   // GET or POST
            String path   = firstLine.split(" ")[1];   // /metrics or /data or /

            if (method.equals("POST") && path.equals("/metrics")) {
                receiveMetrics(in, client.getOutputStream(), store);
                client.close();

            } else if (method.equals("GET")) {
                // pass the already-open socket to the dashboard handler
                // NOTE: we already read the first line, so DashboardHandler skips re-reading it
                dash.handle(client, path);

            } else {
                client.close();
            }

        } catch (Exception e) {
            System.out.println("[Server] Error: " + e.getMessage());
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    // receives a JSON reading from an agent, prints it, and saves it
    private static void receiveMetrics(BufferedReader in, OutputStream out, MetricsStore store) throws Exception {
        // read HTTP headers to find Content-Length
        int contentLength = 0;
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.toLowerCase().startsWith("content-length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
        }

        // read the JSON body
        char[] body = new char[contentLength];
        in.read(body, 0, contentLength);
        String json = new String(body).trim();

        // print to terminal (same as before)
        printToTerminal(json);

        // save to the store so the dashboard can read it
        saveToStore(json, store);

        // send 200 OK back to the agent
        String response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n";
        out.write(response.getBytes("UTF-8"));
    }

    // prints a formatted line to the terminal - same as the old FakeServer
    private static void printToTerminal(String json) {
        try {
            String machine  = parseString(json, "machine");
            double cpu      = parseDouble(json, "cpu");
            double mem      = parseDouble(json, "mem");
            double disk     = parseDouble(json, "disk");
            String memUsed  = parseString(json, "memUsed");
            String memTotal = parseString(json, "memTotal");

            java.time.LocalTime t = java.time.LocalTime.now();
            String time = String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());

            double worst  = Math.max(cpu, Math.max(mem, disk));
            String status = worst >= 90 ? "HIGH" : worst >= 70 ? "WARN" : "OK";

            System.out.printf("[%s] %-20s | CPU: %5.1f%% | RAM: %5.1f%% (%s / %s) | Disk: %5.1f%% | %s%n",
                    time, machine, cpu, mem, memUsed, memTotal, disk, status);

        } catch (Exception e) {
            System.out.println("[Server] Could not read data");
        }
    }

    // parses the JSON and saves a Snapshot to the store
    private static void saveToStore(String json, MetricsStore store) {
        try {
            String machine   = parseString(json, "machine");
            double cpu       = parseDouble(json, "cpu");
            double mem       = parseDouble(json, "mem");
            double disk      = parseDouble(json, "disk");
            String memUsed   = parseString(json, "memUsed");
            String memTotal  = parseString(json, "memTotal");
            String diskUsed  = parseStringOrDefault(json, "diskUsed",  "N/A");
            String diskTotal = parseStringOrDefault(json, "diskTotal", "N/A");

            store.save(new MetricsStore.Snapshot(
                    machine, cpu, mem, disk,
                    memUsed, memTotal, diskUsed, diskTotal));

        } catch (Exception e) {
            System.out.println("[Server] Could not save data: " + e.getMessage());
        }
    }

    // ── small JSON helpers (same as before) ──────────────────────────────────

    private static double parseDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search) + search.length();
        int end   = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return Double.parseDouble(json.substring(start, end).trim());
    }

    private static String parseString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search) + search.length();
        int end   = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private static String parseStringOrDefault(String json, String key, String fallback) {
        try { return parseString(json, key); } catch (Exception e) { return fallback; }
    }
}
