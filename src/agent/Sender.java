package agent;

import java.net.*;

public class Sender {

    private final String host;
    private final int    port;
    private final String machine;

    public Sender(String host, int port, String machine) {
        this.host    = host;
        this.port    = port;
        this.machine = machine;
    }

    public void send(SystemMonitor.Snapshot snap) {
        try {
            String json = snap.toJson().replace("{", "{\"machine\":\"" + machine + "\",");
            byte[] data = json.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
            }
        } catch (Exception e) {
            System.out.println("[Agent] Could not reach server: " + e.getMessage());
        }
    }
}
