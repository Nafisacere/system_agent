package agent;

public class Main {

    private static final int INTERVAL = 1000;
    private static final String SERVER = "http://localhost:9000/metrics";

    // get the computer name automatically
    private static final String MACHINE = getMachineName();

    private static String getMachineName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName().toUpperCase();
        } catch (Exception e) {
            return "UNKNOWN-MACHINE";
        }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("SYSTEM AGENT");
        System.out.println("Machine: " + MACHINE);
        System.out.println("Sending to: " + SERVER);
        System.out.println();

        SystemMonitor monitor = new SystemMonitor();
        Sender sender = new Sender(SERVER, MACHINE);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Agent] Stopped. Goodbye!");
        }));

        while (true) {
            try {
                SystemMonitor.Snapshot snap = monitor.collect();

                // get current time for the log
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