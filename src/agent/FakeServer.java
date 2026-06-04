package agent;

import java.io.*;
import java.net.*;

public class FakeServer {

    private static final int HTTP_PORT  = 9000;
    private static final int UDP_PORT   = 9001;
    private static final String HTML_FOLDER = "src/agent";

    public static void main(String[] args) throws Exception {

        MetricsStore store = new MetricsStore();
        DashboardHandler dash = new DashboardHandler(store, HTML_FOLDER);

        System.out.println("MONITORING SERVER");
        System.out.println("Agents send data to : port " + UDP_PORT + " (UDP)");
        System.out.println("Dashboard           : http://localhost:" + HTTP_PORT + "/");
        System.out.println();

        new Thread(() -> listenUdp(store)).start();

        ServerSocket httpServer = new ServerSocket(HTTP_PORT);
        while (true) {
            Socket client = httpServer.accept();
            new Thread(() -> handleHttp(client, dash)).start();
        }
    }

    private static void listenUdp(MetricsStore store) {
        try (DatagramSocket udpSocket = new DatagramSocket(UDP_PORT)) {
            System.out.println("[Server] Listening on UDP port " + UDP_PORT);
            byte[] buffer = new byte[65535];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                String json   = new String(packet.getData(), 0, packet.getLength(), "UTF-8").trim();
                String fromIp = packet.getAddress().getHostAddress();

                printToTerminal(json, fromIp);
                saveToStore(json, store);
            }
        } catch (Exception e) {
            System.out.println("[Server] UDP error: " + e.getMessage());
        }
    }

    private static void handleHttp(Socket client, DashboardHandler dash) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String firstLine = in.readLine();
            if (firstLine == null) { client.close(); return; }

            String method = firstLine.split(" ")[0];
            String path   = firstLine.split(" ")[1];

            if (method.equals("GET")) {
                dash.handle(client, path);
            } else {
                client.close();
            }
        } catch (Exception e) {
            System.out.println("[Server] HTTP error: " + e.getMessage());
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private static void printToTerminal(String json, String fromIp) {
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

            System.out.printf("[%s] %-20s | CPU: %5.1f%% | RAM: %5.1f%% (%s / %s) | Disk: %5.1f%% | %s  [from %s]%n",
                    time, machine, cpu, mem, memUsed, memTotal, disk, status, fromIp);
        } catch (Exception e) {
            System.out.println("[Server] Could not read packet");
        }
    }

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
