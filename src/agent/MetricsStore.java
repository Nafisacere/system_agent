package agent;

import java.sql.*;
import java.util.*;

public class MetricsStore {

    private static final int MAX_HISTORY = 60;
    private final String dbPath;

    public static class Snapshot {
        public final String machine;
        public final double cpu;
        public final double mem;
        public final double disk;
        public final String memUsed;
        public final String memTotal;
        public final String diskUsed;
        public final String diskTotal;
        public final long   time;

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

        public Snapshot(String machine, double cpu, double mem, double disk,
                        String memUsed, String memTotal,
                        String diskUsed, String diskTotal, long time) {
            this.machine   = machine;
            this.cpu       = cpu;
            this.mem       = mem;
            this.disk      = disk;
            this.memUsed   = memUsed;
            this.memTotal  = memTotal;
            this.diskUsed  = diskUsed;
            this.diskTotal = diskTotal;
            this.time      = time;
        }
    }

    public MetricsStore(String dbPath) {
        this.dbPath = dbPath;
        initDb();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private void initDb() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.out.println("[Server] SQLite driver not found: " + e.getMessage());
        }
        String sql = "CREATE TABLE IF NOT EXISTS metrics ("
                + "id        INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "machine   TEXT    NOT NULL,"
                + "cpu       REAL    NOT NULL,"
                + "ram       REAL    NOT NULL,"
                + "disk      REAL    NOT NULL,"
                + "memUsed   TEXT,"
                + "memTotal  TEXT,"
                + "diskUsed  TEXT,"
                + "diskTotal TEXT,"
                + "ts        INTEGER NOT NULL"
                + ")";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("[Server] Database ready: " + dbPath);
        } catch (SQLException e) {
            System.out.println("[Server] Database error: " + e.getMessage());
        }
    }

    private boolean machineExists(String machine) {
        String sql = "SELECT COUNT(*) FROM metrics WHERE machine = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, machine);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void save(Snapshot snap) {
        if (!machineExists(snap.machine)) {
            System.out.println("[Server] New machine connected: " + snap.machine);
        }
        String sql = "INSERT INTO metrics (machine, cpu, ram, disk, memUsed, memTotal, diskUsed, diskTotal, ts) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, snap.machine);
            ps.setDouble(2, snap.cpu);
            ps.setDouble(3, snap.mem);
            ps.setDouble(4, snap.disk);
            ps.setString(5, snap.memUsed);
            ps.setString(6, snap.memTotal);
            ps.setString(7, snap.diskUsed);
            ps.setString(8, snap.diskTotal);
            ps.setLong  (9, snap.time);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[Server] Could not save snapshot: " + e.getMessage());
        }
    }

    public synchronized String toJson() {
        List<String> machineNames = getMachineNames();

        StringBuilder sb = new StringBuilder();
        sb.append("{");

        sb.append("\"machines\":[");
        for (int i = 0; i < machineNames.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(machineNames.get(i)).append("\"");
        }
        sb.append("],");

        sb.append("\"data\":{");
        for (int i = 0; i < machineNames.size(); i++) {
            if (i > 0) sb.append(",");
            String name = machineNames.get(i);
            sb.append("\"").append(name).append("\":");
            appendMachineJson(sb, name);
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    private List<String> getMachineNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT machine FROM metrics GROUP BY machine ORDER BY MIN(ts)";
        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("machine"));
            }
        } catch (SQLException e) {
            System.out.println("[Server] Could not get machine names: " + e.getMessage());
        }
        return names;
    }

    // returns true if the machine sent data in the last 10 seconds
    private boolean isOnline(String machine) {
        String sql = "SELECT MAX(ts) FROM metrics WHERE machine = ?";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, machine);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long lastSeen = rs.getLong(1);
                return (System.currentTimeMillis() - lastSeen) < 10000;
            }
        } catch (SQLException e) {
            // ignore
        }
        return false;
    }

    private void appendMachineJson(StringBuilder sb, String machine) {
        List<Snapshot> history = getHistory(machine);
        Snapshot latest = history.isEmpty() ? null : history.get(history.size() - 1);
        boolean online  = isOnline(machine);

        sb.append("{");
        sb.append("\"online\":").append(online).append(",");
        sb.append("\"latest\":");
        appendSnapshot(sb, latest);

        sb.append(",\"cpu\":");
        appendSeries(sb, history, "cpu");

        sb.append(",\"mem\":");
        appendSeries(sb, history, "mem");

        sb.append(",\"disk\":");
        appendSeries(sb, history, "disk");

        sb.append("}");
    }

    private List<Snapshot> getHistory(String machine) {
        List<Snapshot> list = new ArrayList<>();
        String sql = "SELECT * FROM (SELECT * FROM metrics WHERE machine = ? ORDER BY ts DESC LIMIT ?) ORDER BY ts ASC";
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, machine);
            ps.setInt(2, MAX_HISTORY);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Snapshot(
                    rs.getString("machine"),
                    rs.getDouble("cpu"),
                    rs.getDouble("ram"),
                    rs.getDouble("disk"),
                    rs.getString("memUsed"),
                    rs.getString("memTotal"),
                    rs.getString("diskUsed"),
                    rs.getString("diskTotal"),
                    rs.getLong("ts")
                ));
            }
        } catch (SQLException e) {
            System.out.println("[Server] Could not get history: " + e.getMessage());
        }
        return list;
    }

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

    // ── History endpoint ──────────────────────────────────────────────────────
    // returns paginated rows from the database as JSON
    public synchronized String historyJson(String machine, long rangeMs, int page, int size) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        long since = rangeMs > 0 ? System.currentTimeMillis() - rangeMs : 0;

        String countSql = "SELECT COUNT(*) FROM metrics WHERE 1=1"
                + (machine.isEmpty() ? "" : " AND machine=?")
                + (since > 0 ? " AND ts>=?" : "");

        String dataSql = "SELECT * FROM metrics WHERE 1=1"
                + (machine.isEmpty() ? "" : " AND machine=?")
                + (since > 0 ? " AND ts>=?" : "")
                + " ORDER BY ts DESC LIMIT ? OFFSET ?";

        int total = 0;
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(countSql)) {
            int idx = 1;
            if (!machine.isEmpty()) ps.setString(idx++, machine);
            if (since > 0)         ps.setLong(idx++, since);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) total = rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("[Server] History count error: " + e.getMessage());
        }

        sb.append("\"total\":").append(total).append(",");
        sb.append("\"page\":").append(page).append(",");
        sb.append("\"size\":").append(size).append(",");
        sb.append("\"rows\":[");

        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(dataSql)) {
            int idx = 1;
            if (!machine.isEmpty()) ps.setString(idx++, machine);
            if (since > 0)         ps.setLong(idx++, since);
            ps.setInt(idx++, size);
            ps.setInt(idx++, page * size);
            ResultSet rs = ps.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{")
                  .append("\"id\":").append(rs.getInt("id")).append(",")
                  .append("\"machine\":\"").append(rs.getString("machine")).append("\",")
                  .append("\"cpu\":").append(String.format("%.1f", rs.getDouble("cpu"))).append(",")
                  .append("\"ram\":").append(String.format("%.1f", rs.getDouble("ram"))).append(",")
                  .append("\"disk\":").append(String.format("%.1f", rs.getDouble("disk"))).append(",")
                  .append("\"memUsed\":\"").append(rs.getString("memUsed")).append("\",")
                  .append("\"memTotal\":\"").append(rs.getString("memTotal")).append("\",")
                  .append("\"diskUsed\":\"").append(rs.getString("diskUsed")).append("\",")
                  .append("\"diskTotal\":\"").append(rs.getString("diskTotal")).append("\",")
                  .append("\"ts\":").append(rs.getLong("ts"))
                  .append("}");
            }
        } catch (SQLException e) {
            System.out.println("[Server] History data error: " + e.getMessage());
        }

        sb.append("]}");
        return sb.toString();
    }

    // ── Stats endpoint ────────────────────────────────────────────────────────
    // returns avg/min/max for cpu, ram, disk for a given machine and time range
    public synchronized String statsJson(String machine, long rangeMs) {
        long since = rangeMs > 0 ? System.currentTimeMillis() - rangeMs : 0;

        String sql = "SELECT machine,"
                + " AVG(cpu), MIN(cpu), MAX(cpu),"
                + " AVG(ram), MIN(ram), MAX(ram),"
                + " AVG(disk),MIN(disk),MAX(disk),"
                + " COUNT(*)"
                + " FROM metrics WHERE 1=1"
                + (machine.isEmpty() ? "" : " AND machine=?")
                + (since > 0 ? " AND ts>=?" : "")
                + " GROUP BY machine";

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            if (!machine.isEmpty()) ps.setString(idx++, machine);
            if (since > 0)         ps.setLong(idx++, since);
            ResultSet rs = ps.executeQuery();
            boolean first = true;
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("{")
                  .append("\"machine\":\"").append(rs.getString(1)).append("\",")
                  .append("\"cpuAvg\":").append(String.format("%.1f", rs.getDouble(2))).append(",")
                  .append("\"cpuMin\":").append(String.format("%.1f", rs.getDouble(3))).append(",")
                  .append("\"cpuMax\":").append(String.format("%.1f", rs.getDouble(4))).append(",")
                  .append("\"ramAvg\":").append(String.format("%.1f", rs.getDouble(5))).append(",")
                  .append("\"ramMin\":").append(String.format("%.1f", rs.getDouble(6))).append(",")
                  .append("\"ramMax\":").append(String.format("%.1f", rs.getDouble(7))).append(",")
                  .append("\"diskAvg\":").append(String.format("%.1f", rs.getDouble(8))).append(",")
                  .append("\"diskMin\":").append(String.format("%.1f", rs.getDouble(9))).append(",")
                  .append("\"diskMax\":").append(String.format("%.1f", rs.getDouble(10))).append(",")
                  .append("\"count\":").append(rs.getInt(11))
                  .append("}");
            }
        } catch (SQLException e) {
            System.out.println("[Server] Stats error: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Uptime endpoint ───────────────────────────────────────────────────────
    // returns how long each machine has been connected (first seen vs now)
    public synchronized String uptimeJson() {
        String sql = "SELECT machine, MIN(ts), MAX(ts), COUNT(*) FROM metrics GROUP BY machine";
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            boolean first = true;
            long now = System.currentTimeMillis();
            while (rs.next()) {
                if (!first) sb.append(",");
                first = false;
                long firstSeen = rs.getLong(2);
                long lastSeen  = rs.getLong(3);
                long uptimeMs  = now - firstSeen;
                sb.append("{")
                  .append("\"machine\":\"").append(rs.getString(1)).append("\",")
                  .append("\"firstSeen\":").append(firstSeen).append(",")
                  .append("\"lastSeen\":").append(lastSeen).append(",")
                  .append("\"uptimeMs\":").append(uptimeMs).append(",")
                  .append("\"count\":").append(rs.getInt(4))
                  .append("}");
            }
        } catch (SQLException e) {
            System.out.println("[Server] Uptime error: " + e.getMessage());
        }
        sb.append("]");
        return sb.toString();
    }
}
