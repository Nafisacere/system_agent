package agent;

import java.io.*;
import java.net.*;

public class Sender {

    private final String serverUrl;
    private final String machineName;

    public Sender(String serverUrl, String machineName) {
        this.serverUrl   = serverUrl;
        this.machineName = machineName;
    }

    public void send(SystemMonitor.Snapshot snap) {
        try {
            // add the machine name to the json
            String json = snap.toJson().replace("{", "{\"machine\":\"" + machineName + "\",");

            URL url = new URL(serverUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes("UTF-8"));
            }

            conn.getResponseCode();
            conn.disconnect();

        } catch (Exception e) {
            System.out.println("[Agent] Could not reach server: " + e.getMessage());
        }
    }
}