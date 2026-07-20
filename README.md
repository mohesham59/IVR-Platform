# IVR Flow Engine

A simple Java SE engine that reads IVR call flows from JSON scenario files and executes them via Asterisk FastAGI. Supports concurrent calls to the same and different extensions, and dynamic extension registration via a helper bash script.

## Project Structure

```
IVR_platform/
├── pom.xml                          # Maven config (asterisk-java + Gson)
├── add_extension.sh                 # Bash script to register new extensions in Asterisk
├── scenarios/                       # JSON scenario files (configurable path)
│   └── restaurant-booking-001.json  # Example scenario
└── src/main/java/gov/iti/telecom/
    ├── IvrFlowEngine.java           # Main entry point — starts FastAGI server
    ├── IvrAgiScript.java            # Handles each call — walks through JSON nodes
    └── ScenarioLoader.java          # Reads & caches JSON files (thread-safe)
```

## How It Works

1. `IvrFlowEngine` starts a FastAGI server on port **4573**.
2. When Asterisk sends a call via `AGI(agi://host/ivr_platform?business_name=Pizza_Place)`, the server routes it to `IvrAgiScript`.
3. `IvrAgiScript` extracts the `business_name` parameter from the AGI URL. If provided, it replaces spaces with underscores and uses that as the scenario name (e.g. `business_name=Pizza Place` → loads `Pizza_Place.json`). If no `business_name` is given, it falls back to the script name from the URL path.
4. Each node type triggers the corresponding Asterisk action (play audio, collect DTMF, transfer, etc.).

## Audio Files

Audio files referenced in the JSON scenarios (the `"audio"` field) must be placed on the **Asterisk server**, not in this Java project.

### Where to store them

```
/var/lib/asterisk/sounds/
```

For the example scenario, you need these files on the Asterisk server:

| JSON `"audio"` value       | File to place on Asterisk server                     |
|----------------------------|------------------------------------------------------|
| `welcome_restaurant.wav`   | `/var/lib/asterisk/sounds/welcome_restaurant.wav`    |
| `main_menu_prompt.wav`     | `/var/lib/asterisk/sounds/main_menu_prompt.wav`      |
| `enter_date.wav`           | `/var/lib/asterisk/sounds/enter_date.wav`            |
| `enter_time.wav`           | `/var/lib/asterisk/sounds/enter_time.wav`            |
| `enter_party_size.wav`     | `/var/lib/asterisk/sounds/enter_party_size.wav`      |

> **Note:** The code strips the `.wav` extension before sending the filename to Asterisk. Asterisk then picks the best available format automatically (`.wav`, `.gsm`, `.sln`, etc.).

## Extension Registration Script (`add_extension.sh`)

A helper bash script is provided to dynamically add new extensions to the Asterisk dialplan. It appends the required dialplan entries to `/etc/asterisk/extensions.conf`.

### Usage

```bash
./add_extension.sh <extension> <business_name>

# Example
./add_extension.sh 1000 "Pizza Place"
```

This appends the following to `/etc/asterisk/extensions.conf`:

```ini
; Business: Pizza Place
exten => 1000,1,NoOp(Incoming call for Pizza Place)
exten => 1000,n,AGI(agi://127.0.0.1:4573/ivr_platform?business_name=Pizza Place)
exten => 1000,n,Hangup()
```

### Prerequisite: `/etc/asterisk/extensions.conf` Write Permission

The `add_extension.sh` script writes directly to `/etc/asterisk/extensions.conf`. By default, this file is owned by `root` (or the `asterisk` user) and is **not writable** by regular users. You must grant write permission **before** running the script — either manually or from Java code.

**Option 1 — Grant write permission once (recommended for development):**

```bash
sudo chmod o+w /etc/asterisk/extensions.conf
```

**Option 2 — Change ownership to your user:**

```bash
sudo chown $(whoami) /etc/asterisk/extensions.conf
```

**Option 3 — Add your user to the `asterisk` group:**

```bash
sudo usermod -aG asterisk $(whoami)
sudo chmod g+w /etc/asterisk/extensions.conf
# Log out and back in for group changes to take effect
```

> **Important:** If you intend to call `add_extension.sh` from Java code (e.g. via `ProcessBuilder` or `Runtime.exec()`), the Java process must run as a user that has write access to `/etc/asterisk/extensions.conf`. The script does **not** use `sudo` internally, so the permission must already be in place. Example Java invocation:
>
> ```java
> ProcessBuilder pb = new ProcessBuilder(
>     "./add_extension.sh", "1000", "Pizza Place"
> );
> pb.inheritIO();
> Process process = pb.start();
> int exitCode = process.waitFor();
> ```
>
> If the file is not writable, the script will print an error and exit with code `1`.

After modifying `extensions.conf`, reload the Asterisk dialplan:

```bash
asterisk -rx "dialplan reload"
```

## JSON Scenario Format

Each scenario file defines a list of nodes. The engine starts at the first node and follows `"next"` links.

### Node Types

| Type       | Description                                           |
|------------|-------------------------------------------------------|
| `play`     | Plays an audio file, then moves to `next`             |
| `menu`     | Plays audio, collects DTMF, routes by digit choice    |
| `form`     | Collects multiple fields sequentially                 |
| `transfer` | Transfers the call to a SIP destination               |

### Fields in the JSON

- `"audio"` — the `.wav` filename Asterisk will play (required for `play`, `menu`, and `form` fields)
- `"prompt"` — human-readable description for logging only (not played to the caller)
- `"next"` — the `id` of the next node to go to (use `"end"` to hang up)

## Running

```bash
# Build
mvn compile

# Run with default scenarios directory
java -cp target/classes gov.iti.telecom.IvrFlowEngine

# Run with a custom scenarios directory
java -cp target/classes gov.iti.telecom.IvrFlowEngine /path/to/scenarios
```

## Asterisk Dialplan

### Manual setup

Add extensions directly in `/etc/asterisk/extensions.conf`:

```ini
[default]
; Simple extension pointing to a scenario by script name
exten => 100,1,AGI(agi://your-java-server:4573/restaurant-booking-001)

; Extension using business_name parameter (recommended)
exten => 1000,1,NoOp(Incoming call for Pizza Place)
exten => 1000,n,AGI(agi://your-java-server:4573/ivr_platform?business_name=Pizza_Place)
exten => 1000,n,Hangup()
```

### Automated setup

Use the provided script:

```bash
./add_extension.sh 1000 "Pizza Place"
```

The `business_name` parameter in the AGI URL maps to a scenario file: `business_name=Pizza Place` → `scenarios/Pizza_Place.json`. If no `business_name` is given, the script name from the URL path is used instead (e.g. `/restaurant-booking-001` → `scenarios/restaurant-booking-001.json`).

## Concurrency

- Each call runs on its own thread from the FastAGI thread pool.
- All per-call state is in local variables — no shared mutable state.
- The scenario cache uses `ConcurrentHashMap` for thread-safe loading.
- Multiple callers can hit the same or different extensions simultaneously.
