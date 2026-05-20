This is for my internship at Ooredoo qatar

# System Agent
task: build a monitoring agent that collects CPU, RAM and disk usage
and sends it to a server every .

## How it works
[Agent] --> collects CPU/RAM/Disk --> sends JSON --> [Server]

## Files
- `Main.java`          - starts the agent
- `SystemMonitor.java` - reads CPU, RAM, disk from the OS(opersting system)
- `Sender.java`        - sends the data to the server
- `FakeServer.java`    - a test server to receive the data

----------------
 ## How to run  
----------------
### STEP 1 - Compile everything

paste this in the command prompt: javac -encoding UTF-8 -d bin src\agent\*.java

### STEP 2 - Open TWO Command Prompt windows

Window 1 - Starting the fake server first:
java -cp bin agent.FakeServer


Window 2 - Start the agent:
java -cp bin agent.Main


### STEP 3 - Watch the data flow
- Window 2 shows the agent collecting data
- Window 1 shows the server receiving it

## This is to send to a real server
Change this line in Main.java:
private static final String SERVER = "whatever link is givern";

