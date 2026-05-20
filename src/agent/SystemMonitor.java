package agent;

import java.io.*;
import java.nio.file.*;
import java.util.*;

// this class reads the CPU, RAM and disk info from the computer
// it works on both windows and linux
public class SystemMonitor {

    // check what OS we are running on
    private static final boolean IS_WINDOWS =
        System.getProperty("os.name", "").toLowerCase().contains("win");

    // we need these to calculate cpu usage on linux
    private long prevIdle  = 0;
    private long prevTotal = 0;
    private boolean firstRead = true;

    // this holds one reading (snapshot) of all the metrics
    public static class Snapshot {
        public final double cpu;   // cpu usage in percent
        public final double mem;   // memory usage in percent
        public final double disk;  // disk usage in percent
        public final String memUsed;
        public final String memTotal;
        public final String diskUsed;
        public final String diskTotal;
        public final long time;    // when was this reading taken

        Snapshot(double cpu, double mem, double disk,
                 String memUsed, String memTotal,
                 String diskUsed, String diskTotal) {
            this.cpu      = cpu;
            this.mem      = mem;
            this.disk     = disk;
            this.memUsed  = memUsed;
            this.memTotal = memTotal;
            this.diskUsed  = diskUsed;
            this.diskTotal = diskTotal;
            this.time     = System.currentTimeMillis();
        }

        // turn the snapshot into JSON so we can send it to the server
        public String toJson() {
            return "{"
                + "\"cpu\":"        + String.format("%.1f", cpu)  + ","
                + "\"mem\":"        + String.format("%.1f", mem)  + ","
                + "\"disk\":"       + String.format("%.1f", disk) + ","
                + "\"memUsed\":\""  + memUsed   + "\","
                + "\"memTotal\":\"" + memTotal  + "\","
                + "\"diskUsed\":\"" + diskUsed  + "\","
                + "\"diskTotal\":\"" + diskTotal + "\","
                + "\"time\":"       + time
                + "}";
        }
    }

    // this is the main method that gets called every second
    public Snapshot collect() throws Exception {
        if (IS_WINDOWS) {
            return collectWindows();
        } else {
            return collectLinux();
        }
    }

    // ── windows ──────────────────────────────────────────────

    private Snapshot collectWindows() throws Exception {
        double cpu    = getCpu();
        double[] mem  = getMem();
        double[] disk = getDisk();

        double memPct  = mem[1]  > 0 ? (mem[0]  / mem[1])  * 100 : 0;
        double diskPct = disk[1] > 0 ? (disk[0] / disk[1]) * 100 : 0;

        return new Snapshot(
            cpu, memPct, diskPct,
            readable((long) mem[0]),  readable((long) mem[1]),
            readable((long) disk[0]), readable((long) disk[1])
        );
    }

   // ask windows for the cpu usage using powershell
    private double getCpu() throws Exception {
        String result = runPS("(Get-WmiObject Win32_Processor).LoadPercentage");
        result = result.trim();
        return result.isEmpty() ? 0.0 : Double.parseDouble(result);
    }

    // ask windows for the memory info using powershell
    private double[] getMem() throws Exception {
        String total = runPS("(Get-WmiObject Win32_ComputerSystem).TotalPhysicalMemory");
        String free  = runPS("(Get-WmiObject Win32_OperatingSystem).FreePhysicalMemory");
        long t = total.trim().isEmpty() ? 0 : Long.parseLong(total.trim());
        long f = free.trim().isEmpty()  ? 0 : Long.parseLong(free.trim()) * 1024L;
        return new double[]{ t - f, t };
    }

    // get disk space using java built in File class
    private double[] getDisk() {
        File drive = new File("C:\\");
        long total = drive.getTotalSpace();
        long free  = drive.getUsableSpace();
        return new double[]{ total - free, total };
    }

    // run a powershell command and return the output
    private String runPS(String cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line).append("\n");
        p.waitFor();
        return sb.toString().trim();
    }

    // ── linux ──────────────────────────────────────────────

    private Snapshot collectLinux() throws Exception {
        double cpu    = getLinuxCpu();
        double[] mem  = getLinuxMem();
        double[] disk = getLinuxDisk();

        double memPct  = mem[1]  > 0 ? (mem[0]  / mem[1])  * 100 : 0;
        double diskPct = disk[1] > 0 ? (disk[0] / disk[1]) * 100 : 0;

        return new Snapshot(
            cpu, memPct, diskPct,
            readable((long) mem[0]),  readable((long) mem[1]),
            readable((long) disk[0]), readable((long) disk[1])
        );
    }

    private double getLinuxCpu() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("/proc/stat"));
        for (String line : lines) {
            if (!line.startsWith("cpu ")) continue;
            String[] p = line.trim().split("\\s+");
            long user = Long.parseLong(p[1]);
            long nice = Long.parseLong(p[2]);
            long sys  = Long.parseLong(p[3]);
            long idle = Long.parseLong(p[4]);
            long iow  = Long.parseLong(p[5]);
            long irq  = Long.parseLong(p[6]);
            long sirq = Long.parseLong(p[7]);
            long steal = (p.length > 8) ? Long.parseLong(p[8]) : 0;
            long idleTime  = idle + iow;
            long totalTime = user + nice + sys + idle + iow + irq + sirq + steal;
            if (firstRead) {
                prevIdle = idleTime; prevTotal = totalTime;
                firstRead = false;
                return 0.0;
            }
            long di = idleTime  - prevIdle;
            long dt = totalTime - prevTotal;
            prevIdle = idleTime; prevTotal = totalTime;
            return dt > 0 ? (1.0 - (double) di / dt) * 100.0 : 0.0;
        }
        return 0.0;
    }

    private double[] getLinuxMem() throws Exception {
        long total = 0, free = 0, buf = 0, cached = 0, srec = 0;
        for (String line : Files.readAllLines(Paths.get("/proc/meminfo"))) {
            if      (line.startsWith("MemTotal:"))     total  = parseKb(line);
            else if (line.startsWith("MemFree:"))      free   = parseKb(line);
            else if (line.startsWith("Buffers:"))      buf    = parseKb(line);
            else if (line.startsWith("Cached:"))       cached = parseKb(line);
            else if (line.startsWith("SReclaimable:")) srec   = parseKb(line);
        }
        long used = total - (free + buf + cached + srec);
        return new double[]{ used * 1024L, total * 1024L };
    }

    private long parseKb(String line) {
        return Long.parseLong(line.trim().split("\\s+")[1]);
    }

    private double[] getLinuxDisk() {
        File root = new File("/");
        return new double[]{ root.getTotalSpace() - root.getUsableSpace(), root.getTotalSpace() };
    }

    // ── helper ──────────────────────────────────────────────

    // convert bytes to a human readable string like 8.5 GB
    public static String readable(long bytes) {
        if (bytes < 0)               return "N/A";
        if (bytes < 1024)            return bytes + " B";
        if (bytes < 1024 * 1024)     return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L*1024*1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
