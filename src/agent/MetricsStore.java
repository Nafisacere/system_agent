package agent;

import java.util.*;

// this class stores the latest readings from each machine
// it also keeps a history of the last 60 readings for the graphs
public class MetricsStore {

    // how many past readings to keep per machine
    private static final int MAX_HISTORY = 60;

    // one reading from one machine at one point in time
    public static class Snapshot {
        public final String machine;
        public final double cpu;
        public final double mem;
        public final double disk;
        public final String memUsed;
        public final String memTotal;
        public final String diskUsed;
        public final String diskTotal;
        public final long   time;  // when was this taken (milliseconds)

        public Snapshot(String machine, double cpu, double mem, double disk,
                        String memUsed, String memTotal,
                        String diskUsed, String diskTotal) {
            this.machine   = machine;
            this.cpu       = cpu;
            this.mem       = mem;
            this.disk      = disk;
            this.memUsed   = memUsed;
            this.memTotal  = memTotal;
            this.diskUsed  = diskUsed;
            this.diskTotal = diskTotal;
            this.time      = System.currentTimeMillis();
        }
    }

    // stores the latest snapshot + history list for one machine
    private static class MachineData {
        Snapshot         latest  = null;
        List<Snapshot>   history = new ArrayList<>();
    }

    // map of machine name -> its data
    // synchronized because the server thread writes and the dashboard thread reads
    private final Map<String, MachineData> machines = new LinkedHashMap<>();

    // called every time the server receives a new reading from an agent
    public synchronized void save(Snapshot snap) {
        MachineData data = machines.get(snap.machine);
        if (data == null) {
            data = new MachineData();
            machines.put(snap.machine, data);
            System.out.println("[Server] New machine connected: " + snap.machine);
        }
        data.latest = snap;
        data.history.add(snap);
        if (data.history.size() > MAX_HISTORY) {
            data.history.remove(0);  // drop the oldest reading
        }
    }

    // returns all the data as a JSON string for the dashboard to read
    // example: { "machines": ["PC1","PC2"], "data": { "PC1": {...}, ... } }
    public synchronized String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // list of machine names
        sb.append("\"machines\":[");
        boolean firstMachine = true;
        for (String name : machines.keySet()) {
            if (!firstMachine) sb.append(",");
            sb.append("\"").append(name).append("\"");
            firstMachine = false;
        }
        sb.append("],");

        // data for each machine
        sb.append("\"data\":{");
        boolean firstData = true;
        for (Map.Entry<String, MachineData> entry : machines.entrySet()) {
            if (!firstData) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            appendMachine(sb, entry.getValue());
            firstData = false;
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    // builds the JSON block for one machine
    private void appendMachine(StringBuilder sb, MachineData data) {
        sb.append("{");
        sb.append("\"latest\":");
        appendSnapshot(sb, data.latest);

        sb.append(",\"cpu\":");
        appendSeries(sb, data.history, "cpu");

        sb.append(",\"mem\":");
        appendSeries(sb, data.history, "mem");

        sb.append(",\"disk\":");
        appendSeries(sb, data.history, "disk");

        sb.append("}");
    }

    // builds one snapshot as JSON
    private void appendSnapshot(StringBuilder sb, Snapshot s) {
        if (s == null) { sb.append("null"); return; }
        sb.append("{")
          .append("\"machine\":\"").append(s.machine).append("\",")
          .append("\"cpu\":").append(String.format("%.1f", s.cpu)).append(",")
          .append("\"mem\":").append(String.format("%.1f", s.mem)).append(",")
          .append("\"disk\":").append(String.format("%.1f", s.disk)).append(",")
          .append("\"memUsed\":\"").append(s.memUsed).append("\",")
          .append("\"memTotal\":\"").append(s.memTotal).append("\",")
          .append("\"diskUsed\":\"").append(s.diskUsed).append("\",")
          .append("\"diskTotal\":\"").append(s.diskTotal).append("\",")
          .append("\"ts\":").append(s.time)
          .append("}");
    }

    // builds a list of {t, v} points for a chart (e.g. cpu history over time)
    private void appendSeries(StringBuilder sb, List<Snapshot> history, String metric) {
        sb.append("[");
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append(",");
            Snapshot s = history.get(i);
            double value = metric.equals("cpu") ? s.cpu : metric.equals("mem") ? s.mem : s.disk;
            sb.append("{\"t\":").append(s.time).append(",\"v\":").append(String.format("%.1f", value)).append("}");
        }
        sb.append("]");
    }
}
