# IVR Platform — Work Log & End-to-End Testing Guide

This document has two parts:

1. **Work log** — everything that was done during the review-and-fix effort (code-review findings, dockerization, and bugs found while doing end-to-end testing).
2. **Testing guide** — how to build, run, and test the whole platform yourself, from a simulated AGI call to a real Asterisk call running an AI-generated IVR flow.

---

## 1. Stack Overview

The platform is four Docker containers plus a host-side Ollama instance:

```
SIP phone ─▶ Asterisk (andrius/asterisk, host net)
                │ AGI (agi://127.0.0.1:4573/default)
                ▼
           IVR Engine (Java, FastAGI server :4573)
                │ loads scenarios/restaurant_ivr.vxml
                │ TTS: gTTS → asterisk-sounds/ivr-tts/*.wav
                │ ASR: SpeechRecognition (engine container)
                │ LLM: Ollama @ 127.0.0.1:11434 (host)
                ▼
           IVR AI Engine (Java, Tomcat 10 embedded :8081)
                │ REST: /nexusivr-ai-engine/api/v1/ai/...
                │ LLM providers: OpenRouter (primary) / Ollama
                │ Publish: writes .vxml + registers Asterisk extension
                ▼
           IVR WebApp (React + Vite + TS, Nginx :3000 → 80)
```

| Component | Port | Notes |
|-----------|------|-------|
| `ivr-asterisk` | — | host network; uses host `/etc/asterisk` |
| `ivr-engine` | 4573 | FastAGI; host network |
| `ivr-ai-engine` | 8081 | REST API |
| `ivr-webapp` | 3000 | admin UI |
| Ollama (host) | 11434 | `granite4.1:8b` used by the engine's `<ai>` branch |

Key shared volumes:
- `./IVR-engine/scenarios` → engine `/app/scenarios` **and** ai-engine `/app/IVR-engine/scenarios` (same host dir)
- `./asterisk-sounds` → `/var/lib/asterisk/sounds` for all three containers (TTS output visible to Asterisk)
- `/etc/asterisk` + `/var/run/asterisk` → ai-engine (so `add_extension.sh` can edit the dialplan and reload it)

---

## 2. Work Log — Everything Done

### 2.1 Code-review fixes (Java / AI engine)

- **S2 — broken Docker entrypoint:** the engine container was launching the wrong main class. The Dockerfile now runs `gov.iti.telecom.FastAgiServerMain` (the real FastAGI server). Added `FastAgiServerMain.java` as the canonical entry point.
- **S3 — RCE / command-injection hardening:**
  - `SecureXmlFactory.java` (new) — XXE-safe XML parsing used everywhere (`VxmlParser`, `VxmlToModelConverter`, etc.).
  - Scenario names / file paths are sanitized to `[A-Za-z0-9_-]` (no `/`, no `..`, no shell metacharacters) before touching the filesystem.
  - `add_extension.sh` hardened (idempotent `sed -i`, injection-safe quoting, writable-check before editing `/etc/asterisk/extensions.conf`).
  - `FlowModelValidator`, `UnifiedAiEngine`, `SessionMemoryStore`, `XmlLogFormatter`, `VxmlParser`, `VxmlValidator` tightened.
- **S5 — XXE:** all XML parsing routed through the secure factory (disables external entities / DTD loading).

### 2.2 Frontend fixes (IVR-webapp)

- `backendUrl.ts` (new) — single source of truth for the API base URL (was hardcoded/inconsistent).
- `useAIAssistant.ts`, `types.ts`, `nodeConfig.ts`, `vxmlExporter.ts`, `PropertiesPanel.tsx`, `IVRBuilder.tsx`, `AIAssistant.tsx`, `LoginPage.tsx`, and the SuperAdmin/Tenant layouts/screens cleaned up.
- Verified with `tsc --noEmit` and `npm run build` (both clean).

### 2.3 Dockerization fixes

- **`docker-compose.yml`:**
  - `ivr-engine` gained the `./asterisk-sounds:/var/lib/asterisk/sounds` mount (TTS files written by the engine are visible to Asterisk).
  - `ivr-ai-engine` gained `env_file: ./IVR-AI-engine/.env` (LLM provider + API keys reach the app).
- **`IVR-engine/Dockerfile`:** added `python3 python3-pip ffmpeg`, then `pip3 install --break-system-packages gTTS` and `--no-deps SpeechRecognition requests` (a full SpeechRecognition install pulled `pocketsphinx` and filled the disk).
- **`IVR-AI-engine/Dockerfile`:** added `python3 python3-pip ffmpeg` + gTTS (for voice-prompt generation in-container).
- **`IVR-AI-engine/.../web.xml`:** restored `metadata-complete="true"` — all 18 servlets are declared in *both* web.xml and via `@WebServlet`; without it Tomcat 10 refused to boot (`... [AiAnalyticsServlet] ... both mapped to the url-pattern [/api/v1/ai/analytics] ... not permitted`).
- **JVoiceXML startup:** fixed the fixed-timeout wait so the engine container reliably reaches "interpreter STARTED".

### 2.4 Bugs found and fixed during E2E testing

- **`FlowPublishService.java` — hardcoded `/usr/bin/sudo`:** the AI-engine runs as root in Docker where `sudo` does not exist, so extension registration always failed (`Cannot run program "/usr/bin/sudo"`). Now `sudo` is only prepended when *not* running as root and sudo actually exists.
- **`FlowPublishService.java` — tenant-scoped publish directory:** it wrote `scenarios/<tenantId>/<name>.vxml`, but the engine's sanitizer rejects `/` and its loader only reads `scenarios/<name>.vxml`. Publish now writes **flat** into the scenarios root.
- **`ModelToVxmlExporter.java` — bare `<prompt>` forms:** `renderStart`/`renderPrompt` emitted `<prompt>`/`<goto>` directly under `<form>` (no `<block>`), so the engine's `VxmlValidator` rejected every generated flow with *"Form 'start' has no interactive content"*. Prompts are now wrapped in `<block>`, matching the documented design.

### 2.5 Verification status

- AI engine: **391 unit tests pass** (`mvn test` in `IVR-AI-engine`).
- IVR engine: **7 unit tests pass**.
- Frontend: `tsc --noEmit` + `npm run build` clean.
- Full E2E chain proven end-to-end (see Section 8):
  generate → publish → extension registered → real Asterisk call → 0 validation errors → prompt spoken via TTS.

---

## 3. Prerequisites

- Linux host with **Docker** + **Docker Compose v2**.
- **Java 21+** and **Maven 3.9+** (only if you want to build/run the engine on the host).
- **Python 3** with `pip` (host), for the AGI simulator and TTS helper.
- **Ollama** running on the host at `127.0.0.1:11434` with model `granite4.1:8b` (used by the engine's `<ai>` branch):
  ```bash
  ollama pull granite4.1:8b
  curl -s --max-time 120 http://127.0.0.1:11434/api/generate \
    -d '{"model":"granite4.1:8b","prompt":"Say OK","stream":false}'
  ```
- An **`IVR-AI-engine/.env`** file with provider config. Example (values redacted):
  ```
  AI_PROVIDER=openrouter
  OLLAMA_BASE_URL=http://127.0.0.1:11434
  OLLAMA_MODEL=granite4.1:8b
  OLLAMA_TIMEOUT=60
  OPENROUTER_API_KEY=sk-or-...
  OPENROUTER_MODEL=openai/gpt-oss-20b-1:0
  OPENROUTER_TIMEOUT=120
  IVR_ENGINE_SCENARIOS_DIR=.../IVR-engine/scenarios
  IVR_ADD_EXTENSION_SCRIPT=.../IVR-engine/add_extension.sh
  ```
- A running **Asterisk** config on the host (`/etc/asterisk`) so the containers can mount and edit it.

---

## 4. Build & Start the Full Stack

```bash
# from the repository root
docker compose build
docker compose up -d
docker compose ps          # expect 4 containers: asterisk, ivr-engine, ivr-ai-engine, ivr-webapp
```

First boot can take a while (engine/ai-engine run Maven inside the container). Wait until the AI engine answers:

```bash
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/nexusivr-ai-engine/api/v1/ai/providers | grep -q 200; then echo up; break; fi
  sleep 3
done
```

---

## 5. Health Checks

| What | Command | Expected |
|------|---------|----------|
| Engine listening on 4573 | `docker logs ivr-engine` | `FastAGI` / `Listening on *:4573`, JVoiceXML `interpreter STARTED` |
| AI engine API | `curl -s http://localhost:8081/nexusivr-ai-engine/api/v1/ai/providers` | `200` |
| Webapp | `curl -I http://localhost:3000` | `200` |
| Asterisk | `docker exec ivr-asterisk asterisk -rx "core show version"` | version banner |
| Dialplan extension | `docker exec ivr-asterisk asterisk -rx "dialplan show default"` | published extension present |
| TTS output dir | `ls asterisk-sounds/ivr-tts/` | `*.wav/.gsm/.sln` after a call |

---

## 6. The E2E Test Scenarios

Pre-built scenarios live in `IVR-engine/scenarios/`:

| Scenario | What it exercises |
|----------|-------------------|
| `e2e-test.vxml` | Full menu flow: `1` book (fields + grammar + filled), `2` account (`assign`/`var`/`if`/`else`), `3` api (real HTTPS `<api>` call), `4` record (`<record>`), `5` transfer (`<transfer>` + Dial), `0` disconnect |
| `e2e-form-test.vxml` | Form-only doc (no menu): `assign`/`var`/`if`/`goto` — verifies forms render |
| `e2e-ai-test.vxml` | `<ai>` branch: record → ASR → Ollama → jump to a matched form |
| `restaurant_ivr.vxml` | AI-generated flow (published artifact); menu → order_pizza/order_burger/operator |

> Behavior note: when a document contains a `<menu>`, the renderer renders the menu and skips any leading `<form>` welcome block. Use a form-only document if you want the welcome form to run first.

---

## 7. Testing Without Asterisk (AGI Simulator)

`tools/agi_sim.py` speaks the FastAGI protocol directly to the engine on port 4573, so you can drive every renderer branch without a PBX. It answers `GET VARIABLE` (including `VXML_FILE` and `DIALSTATUS`), `STREAM FILE`, `WAIT FOR DIGIT` (pops from `--digits`), `RECORD FILE` (copies `--ai-wav` to `/dev/shm/ai_*.wav`), `EXEC`, `SET VARIABLE`, `HANGUP`, etc.

**Option A — against the dockerized engine (simplest):** the engine container uses host networking, so port 4573 is already reachable:

```bash
python3 tools/agi_sim.py --port 4573 --vxml e2e-test --digits 1 --out /tmp/opencode/sim/callA.log
```

**Option B — engine running on the host:**

```bash
# 1. build the jar (must run from IVR-engine/)
cd IVR-engine
mvn -q -DskipTests clean package
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/ivr-engine-cp.txt

# 2. run the FastAGI server (run from IVR-engine/ so it finds ./scenarios/)
java -cp "target/IVR_platform-1.0-SNAPSHOT.jar:$(cat /tmp/ivr-engine-cp.txt)" \
     gov.iti.telecom.FastAgiServerMain

# 3. in another terminal, simulate a call
python3 tools/agi_sim.py --port 4573 --vxml e2e-test --digits 1 --out /tmp/opencode/sim/callA.log
```

> Stop the docker `ivr-engine` (`docker compose stop ivr-engine`) if you want to bind 4573 from the host instead.

### The 8 calls that prove every branch

```bash
SIM="python3 tools/agi_sim.py --port 4573"
# 1. menu → book → fields → filled
$SIM --vxml e2e-test --digits 1 --out /tmp/opencode/sim/callA.log
# 2. account → if/else (balance=50 → "Your balance is low.")
$SIM --vxml e2e-test --digits 2 --out /tmp/opencode/sim/callB.log
# 3. api → real HTTPS GET (open-meteo) → weather_result
$SIM --vxml e2e-test --digits 3 --out /tmp/opencode/sim/callC.log
# 4. record → voicemail file
$SIM --vxml e2e-test --digits 4 --out /tmp/opencode/sim/callD.log
# 5. transfer → EXEC Dial → DIALSTATUS=ANSWER → "The agent has answered."
$SIM --vxml e2e-test --digits 5 --out /tmp/opencode/sim/callE.log
# 6. disconnect
$SIM --vxml e2e-test --digits 0 --out /tmp/opencode/sim/callF.log
# 7. form-only flow (assign/var/if/goto)
$SIM --vxml e2e-form-test --out /tmp/opencode/sim/callG.log
# 8. AI branch (needs Ollama warmed + a speech wav)
$SIM --vxml e2e-ai-test --digits 1 --ai-wav /tmp/ai_balance.wav --out /tmp/opencode/sim/callH.log
```

Each call transcript is saved to the `--out` file. Look for:
- `VXML_RESULT_*` variables being set (e.g. `VXML_RESULT_balance = 50`, `VXML_RESULT_voice_msg`, `VXML_RESULT_weather_result`),
- the expected spoken text (e.g. `Booking confirmed.`, `You chose balance inquiry.`),
- the session ends with `[VxmlAgiHandler] Call completed successfully`.

### Preparing the AI-branch audio sample

```bash
python3 - <<'EOF'
from gtts import gTTS
gTTS("balance").save("/tmp/ai_balance.mp3")
EOF
ffmpeg -y -i /tmp/ai_balance.mp3 -ar 16000 -ac 1 /tmp/ai_balance.wav
```

---

## 8. Real Asterisk Calls (Docker)

1. Make sure a scenario + extension exist in the dialplan (either from a publish, see Section 9, or add one manually):
   ```bash
   cd IVR-engine
   docker exec ivr-asterisk asterisk -rx "dialplan reload"
   docker exec ivr-asterisk asterisk -rx "dialplan show default" | grep -i restaurant
   ```
2. Originate a call to the extension. **Always use the `/n` flag** on the Local channel, otherwise two racing AGI sessions are spawned:
   ```bash
   docker exec ivr-asterisk asterisk -rx \
     "channel originate Local/560@default/n extension 560@default"
   ```
3. Watch the engine:
   ```bash
   docker logs ivr-engine --since 1m | grep -E "VXML selected|Validation complete|Speaking prompt|Streaming synthesized|Call completed"
   ```
   Expected: `[VxmlValidator] Validation complete. Errors: 0`, `[TtsEngine] Streaming synthesized audio to caller: ivr-tts/tts-en-US-*`, and audio files appear in `asterisk-sounds/ivr-tts/`.

---

## 9. AI Flow: Generate → Publish → Call

This is the full end-to-end product flow.

### 9.1 Generate a flow (LLM)

```bash
curl -s -X POST http://localhost:8081/nexusivr-ai-engine/api/v1/ai/flow/generate \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-ID: 00000000-0000-0000-0000-000000000001' \
  -d '{"description":"A simple restaurant ordering IVR: greet, menu with pizza, burger and operator options","language":"en"}' \
  -o /tmp/opencode/flowgen.json
cat /tmp/opencode/flowgen.json
```

Response contains `id`, `name`, and a JSON-encoded `flowJson` (React Flow graph). Takes ~10–15 s. OpenRouter 502s on the first attempt are retried automatically.

### 9.2 Publish (writes VXML + registers the Asterisk extension)

```bash
python3 - <<'EOF'
import json, urllib.request

resp = json.load(open('/tmp/opencode/flowgen.json'))
payload = {
    "flowId": resp["id"],
    "flowName": resp["name"],
    "extension": "560",                 # free extension in the [default] context
    "flowJson": resp["flowJson"],
}
req = urllib.request.Request(
    "http://localhost:8081/nexusivr-ai-engine/api/v1/ai/flow/publish",
    data=json.dumps(payload).encode(),
    headers={"Content-Type": "application/json"},
)
print(urllib.request.urlopen(req, timeout=60).read().decode())
EOF
```

Expect:
```json
{ "success": true, "filename": "restaurant_ivr.vxml", "extensionRegistered": true,
  "filePath": "/app/IVR-engine/scenarios/restaurant_ivr.vxml", "status": "published", ... }
```

Verify:
- `IVR-engine/scenarios/restaurant_ivr.vxml` exists (with `<block><prompt>…</prompt><goto/></block>` wrappers),
- `/etc/asterisk/extensions.conf` has `exten => 560,1,... Set(VXML_FILE=restaurant_ivr)` then `AGI(agi://127.0.0.1:4573/default)`,
- dialplan reloaded: `docker exec ivr-asterisk asterisk -rx "dialplan reload"`.

### 9.3 Call it

```bash
docker exec ivr-asterisk asterisk -rx "channel originate Local/560@default/n extension 560@default"
docker logs ivr-engine --since 1m | grep -E "Validation complete|Speaking prompt|Call completed"
```

Expected: `Validation complete. Errors: 0` and the menu prompt spoken ("Press 1 for pizza, Press 2 for burger, Press 0 for operator.").

---

## 10. Known Limitations

- **Scenario cache:** `VxmlLoader` caches parsed VXML by name with no invalidation. Publishing a *new* name is picked up immediately; re-publishing an *existing* name requires restarting the engine (`docker compose restart ivr-engine`).
- **JVoiceXML `error.noresource`:** every call logs a non-fatal `error.noresource: Failed to create implementation platform` and `VXML_STATE=ERROR` even though the call renders and completes. Pre-existing; the handler treats it as a normal completion and still sets `VXML_RESULT_*`.
- **No built-in Asterisk prompts:** the `andrius/asterisk` image ships with no native sounds (no `beep`, `vm-goodbye`, etc.) and the shared sounds dir starts empty. TTS-generated prompts are self-contained, so generated flows work; any scenario that plays a native Asterisk prompt would be silent.
- **Menu-first rendering:** documents containing a `<menu>` render the menu and skip leading `<form>` welcome blocks (see Section 6).
- **Root-owned published files:** files written by the publish flow are owned by `root` (container writes them). Remove/edit them via `docker exec ivr-engine rm …` if needed.
- **Duplicate AGI sessions:** originating a Local channel *without* the `/n` flag spawns two racing AGI sessions (caused intermittent "Speech synthesis failed" errors). Always use `/n`.

---

## 11. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Engine doesn't answer on 4573 | Another process holds the port (`ss -ltnp \| grep 4573`); stop the host java engine or `docker compose stop ivr-engine` and retry. |
| `Timeout waiting for JVoiceXML to start` | Network/DNS blocked inside container; JVoiceXML downloads model resources on first boot. Restart `ivr-engine` and check logs. |
| Publish says `extensionRegistered: false` / sudo error | Old image. Rebuild: `docker compose build ivr-ai-engine && docker compose up -d ivr-ai-engine`. |
| Publish VXML fails engine validation ("no interactive content") | Old generated file (cache) or old exporter. Restart engine and re-publish. |
| AI `flow/generate` 502 | OpenRouter transient error; the retry path should succeed. Check `docker logs ivr-ai-engine` for the second attempt. |
| `no space left on device` in builds | `docker system prune -f` (reclaims several GB). |
| TTS silent on the call | Confirm `asterisk-sounds/ivr-tts/` has files after the call and that Asterisk's `/var/lib/asterisk/sounds` mount is the shared volume. |
| Originate spawns duplicate AGI sessions | Use `Local/<ext>@default/n` (the `/n` disables the calling leg's dialplan execution). |

---

## 12. Key Files

- `docker-compose.yml` — the 4-service stack (mounts, env_file, host networking).
- `IVR-engine/Dockerfile`, `IVR-AI-engine/Dockerfile` — python/ffmpeg/gTTS deps, entrypoints.
- `IVR-engine/src/main/java/gov/iti/telecom/FastAgiServerMain.java` — engine entry point.
- `IVR-engine/src/main/java/gov/iti/telecom/VxmlAgiHandler.java` — AGI→VXML orchestration.
- `IVR-engine/src/main/java/gov/iti/telecom/VxmlLoader.java` — scenario loading + cache.
- `IVR-engine/src/main/java/gov/iti/telecom/VxmlValidator.java` — scenario validation.
- `IVR-engine/src/main/java/gov/iti/telecom/TtsEngine.java` — streaming TTS to the channel.
- `IVR-engine/src/main/java/gov/iti/telecom/OllamaAgent.java` — `<ai>` LLM calls.
- `IVR-engine/add_extension.sh` — dialplan registration script.
- `IVR-AI-engine/src/main/java/com/nexusivr/ai/service/FlowPublishService.java` — publish pipeline (flat write + extension registration).
- `IVR-AI-engine/src/main/java/com/nexusivr/ai/service/ModelToVxmlExporter.java` — model → VXML (block-wrapped prompts).
- `IVR-AI-engine/src/main/java/com/nexusivr/ai/util/SecureXmlFactory.java` — XXE-safe XML parsing.
- `IVR-AI-engine/src/main/java/com/nexusivr/ai/controller/AiFlowServlet.java` — `/api/v1/ai/flow/generate` + `/publish` endpoints.
- `IVR-engine/scenarios/` — VXML scenarios (e2e tests + published flows).
- `IVR-webapp/src/api/backendUrl.ts` — frontend API base URL.
- `tools/agi_sim.py` — FastAGI protocol simulator.
