# System Agent
A system monitoring tool built in Java that tracks CPU, RAM, and disk usage across multiple machines and displays everything on a live web dashboard.

Built during my internship at Ooredoo Qatar.
The main task was to build a monitoring agent that collects system data from multiple machines and reports it to a central server.

---

## What it does
- Agents run on each machine and collect system stats every 500ms
- Data is sent to a central server using UDP sockets
- The server saves everything to a SQLite database
- A web dashboard at `http://localhost:9000` shows live stats, charts, and history

---

## Requirements
- Java 21 or higher
- SQLite JDBC driver (download below)

---

## Setup

**1. Clone the repo**
```
git clone https://github.com/Nafisacere/system_agent.git
cd system_agent
```

**2. Download the SQLite driver**

Download `sqlite-jdbc-3.36.0.3.jar` from:
```
https://github.com/xerial/sqlite-jdbc/releases/download/3.36.0.3/sqlite-jdbc-3.36.0.3.jar
```
Create a `lib` folder in the project and put the jar file inside it.

**3. Compile**
```
javac -cp "lib/sqlite-jdbc-3.36.0.3.jar" -d out src/agent/*.java
```

**4. Run**

Start the server:
```
java -cp "out;lib/sqlite-jdbc-3.36.0.3.jar" agent.FakeServer
```
Start the agent on the same machine:
```
java -cp "out;lib/sqlite-jdbc-3.36.0.3.jar" agent.Main
```
Or on Windows just double-click `Start.bat` and it does everything automatically.

Open the dashboard at `http://localhost:9000` and log in with:
- Username: `admin`
- Password: `admin123`

---

## Running the agent on another machine

Pass the server IP as an argument:
```
java -cp out agent.Main 192.168.1.x
```
On a VirtualBox Linux VM (NAT mode):
```
java -cp out agent.Main 10.0.2.2
```

---

## Dashboard

**System Agent tab** — live CPU, RAM and disk stats for the selected machine

**Multi-View tab** — compare all machines side by side on the same charts

**History tab** — full reading history with filters, stats (avg/min/max), uptime info and CSV export

**Alarms tab** — automatic alerts when CPU, RAM, or Disk usage gets too high, with severity levels (Minor, Major, Critical), duration tracking, and the ability to manually resolve each incident

---

## Project structure

```
src/agent/
  Main.java             - agent entry point
  Sender.java           - sends data via UDP
  SystemMonitor.java    - reads CPU/RAM/disk from the OS
  FakeServer.java       - receives UDP data, serves the dashboard
  MetricsStore.java     - saves and reads data from SQLite
  DashboardHandler.java - handles all HTTP requests
  dashboard.html        - the web dashboard
  login.html            - login page
lib/
  sqlite-jdbc-3.36.0.3.jar  (download separately, not included)
Start.bat             - starts everything on Windows
Stop.bat              - stops everything
