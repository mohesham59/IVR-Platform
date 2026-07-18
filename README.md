# IVR Flow Engine

A simple Java SE engine that reads IVR call flows from JSON scenario files and executes them via Asterisk FastAGI. Supports concurrent calls to the same and different extensions.

## Project Structure

```
IVR_platform/
├── pom.xml                          # Maven config (asterisk-java + Gson)
├── scenarios/                       # JSON scenario files (configurable path)
│   └── restaurant-booking-001.json  # Example scenario
└── src/main/java/gov/iti/telecom/
    ├── IvrFlowEngine.java           # Main entry point — starts FastAGI server
    ├── IvrAgiScript.java            # Handles each call — walks through JSON nodes
    └── ScenarioLoader.java          # Reads & caches JSON files (thread-safe)
```

## How It Works

1. `IvrFlowEngine` starts a FastAGI server on port **4573**.
2. When Asterisk sends a call via `AGI(agi://host/restaurant-booking-001)`, the server routes it to `IvrAgiScript`.
3. `IvrAgiScript` extracts the scenario name from the URL, loads the matching JSON via `ScenarioLoader`, and walks through the flow nodes.
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

# Run with default scenarios directory (./scenarios/)
java -cp target/classes gov.iti.telecom.IvrFlowEngine

# Run with a custom scenarios directory
java -cp target/classes gov.iti.telecom.IvrFlowEngine /path/to/scenarios
```

## Asterisk Dialplan

```ini
; In extensions.conf
exten => 100,1,AGI(agi://your-java-server:4573/restaurant-booking-001)
```

The script name in the URL (`restaurant-booking-001`) maps to `scenarios/restaurant-booking-001.json`.

## Concurrency

- Each call runs on its own thread from the FastAGI thread pool.
- All per-call state is in local variables — no shared mutable state.
- The scenario cache uses `ConcurrentHashMap` for thread-safe loading.
- Multiple callers can hit the same or different extensions simultaneously.
