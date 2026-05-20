package agent;

import java.io.*;
import java.net.*;

public class FakeServer {

    private static final int PORT = 9000;

    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(PORT);

        System.out.println("MONITORING SERVER");
        System.out.println("Waiting for agents to connect...");
        System.out.println();

        while (true) {
            Socket client = server.accept();
            handleRequest(client);
        }
    }

    private static void handleRequest(Socket client) {
        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream   out = client.getOutputStream()
        ) {
            int contentLength = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.split(":")[1].trim());
                }
            }

            char[] body = new char[contentLength];
            in.read(body, 0, contentLength);
            String json = new String(body).trim();

            printFormatted(json);

            String response = "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n";
            out.write(response.getBytes("UTF-8"));

        } catch (Exception e) {
            System.out.println("[Server] Error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private static void printFormatted(String json) {
        try {
            String machine = parseString(json, "machine");
            double cpu     = parseDouble(json, "cpu");
            double mem     = parseDouble(json, "mem");
            double disk    = parseDouble(json, "disk");
            String memUsed  = parseString(json, "memUsed");
            String memTotal = parseString(json, "memTotal");

            // get current time
            java.time.LocalTime t = java.time.LocalTime.now();
            String time = String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());

            double worst = Math.max(cpu, Math.max(mem, disk));
            String status = worst >= 90 ? "HIGH" : worst >= 70 ? "WARN" : "OK";

            System.out.printf("[%s] %-20s | CPU: %5.1f%% | RAM: %5.1f%% (%s / %s) | Disk: %5.1f%% | %s%n",
                time, machine, cpu, mem, memUsed, memTotal, disk, status);

        } catch (Exception e) {
            System.out.println("[Server] Could not read data");
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
}