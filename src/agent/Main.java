package agent;

public class Main {

    private static final int PORT     = 9001;
    private static final int INTERVAL = 500;

    private static final String MACHINE = getMachineName();

    private static String getMachineName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName().toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    public static void main(String[] args) throws Exception {

        String host = args.length > 0 ? args[0] : "localhost";

        System.out.println("SYSTEM AGENT");
        System.out.println("Machine   : " + MACHINE);
        System.out.println("Sending to: " + host + ":" + PORT + " (UDP)");
        System.out.println();

        SystemMonitor monitor = new SystemMonitor();
        Sender sender = new Sender(host, PORT, MACHINE);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Agent] Stopped.");
        }));

        while (true) {
            try {
                SystemMonitor.Snapshot snap = monitor.collect();

                java.time.LocalTime t = java.time.LocalTime.now();
                String time = String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());

                System.out.printf("[%s] CPU: %.1f%%  |  RAM: %.1f%%  |  Disk: %.1f%%  --> Sent%n",
                    time, snap.cpu, snap.mem, snap.disk);

                sender.send(snap);

            } catch (Exception e) {
                System.out.println("[Agent] Error: " + e.getMessage());
            }

            Thread.sleep(INTERVAL);
        }
    }
}
