# Bilingual Demo Test Guide (`bilingual_demo.vxml`)

End-to-end test of the FastAGI VXML renderer with an Arabic/English IVR scenario.

## What it covers

| Menu digit | Scenario | Renderer features exercised |
|---|---|---|
| 1 | Book | `field` + `grammar` (DTMF, `#`-terminated), `${var}` substitution |
| 2 | Account | `assign`/`var`, session `language` switch, `if`/`then`/`else`, `${var}` substitution |
| 3 | API | `api` (HTTP GET), `jsonPath`, `saveResultAs`, result spoken via substitution |
| 4 | Record | `record` (beep + DTMF term), recorded path returned |
| 5 | AI assistant | `ai` → AGI record → Google ASR → Ollama decision → jump to dialog |
| 6 | Transfer | `transfer` with `filled`/`catch` (DIALSTATUS) |
| 7 | Custom audio | `audio` with TTS fallback when the file is missing |
| 0 | Exit | `disconnect` |

Bilingual output everywhere: prompts carry `xml:lang="ar"` / `xml:lang="en"`; Arabic TTS and English TTS are synthesized by gTTS and cached under `ivr-tts/tts-ar-*.wav` / `tts-en-*.wav`. Arabic ASR (`ar-EG`) is selected automatically when the session language starts with `ar`.

## Prerequisites

- Stack up: `docker compose up -d` (ivr-engine on FastAGI port `4573`, host `4573`).
- Ollama reachable from the engine (host port `11434`) — used by the `ai` option.
- `tools/agi_sim.py` in the repo (host simulator for AGI).

## Run via the AGI simulator (no phone needed)

```bash
python3 tools/agi_sim.py --port 4573 --vxml bilingual_demo --digits <d> \
  --ai-wav /tmp/opencode/ai_balance.wav --out /tmp/opencode/sim/bil_<d>.log
```

| digits | Expected |
|---|---|
| `1` | Menu spoken in Arabic then English; `VXML_RESULT_user_choice=1` |
| `12` | field collects `2`; `VXML_RESULT_movie_selection=2`; "You selected option 2" |
| `2` | Arabic session switch ("تغيير اللغة إلى العربية. رصيدك الحالي هو 50 جنيه"), `رصيدك منخفض.` (50 < 100 branch), `الحساب نشط.`, then back to English; `VXML_RESULT_balance=50`, `VXML_RESULT_status=active`, `VXML_RESULT_language=en` |
| `3` | Live `api.open-meteo.com` call; `VXML_RESULT_weather_timezone=Europe/Berlin`; spoken in both languages |
| `4` | `VXML_RESULT_voice_msg` points to a `.wav` recording |
| `5` | beep → ASR "balance" → Ollama `{"status":"FINAL","reply":...,"action":"balance_info"}` → "Jumping to dialog: balance_info" → "Your current balance is five thousand pounds." |
| `6` | `DIALSTATUS=CHANUNAVAIL` (6001 not registered) → catch branch "The agent is unavailable. Goodbye." |
| `7` | Engine log `Audio file not found, using TTS fallback for: welcome_custom.wav`; fallback text spoken in both languages |
| `0` | "Thank you for calling. Goodbye." then hangup |

Engine logs (`docker logs ivr-engine --since 5m`) show `Loading VXML from file: /app/scenarios/bilingual_demo.vxml`, `Validation complete. Errors: 0`, and per-step `Speaking prompt: ...` lines.

## Run against real Asterisk

1. Publish `bilingual_demo` (or drop the file under `IVR-engine/scenarios/`).
2. If the scenario name was already loaded once, restart the engine first (the scenario cache has no invalidation):
   `docker compose restart ivr-engine`
3. Add a temporary dialplan entry (e.g. extension `560` → `Answer()` → `AGI(agi://127.0.0.1:4573/bilingual_demo)`), then:
   `asterisk -rx "channel originate Local/560@default/n extension s@default application AGI agi://127.0.0.1:4573/bilingual_demo"`

## Docker fixes required to make this pass (already applied)

1. **`<ai>` / `<record>` recordings are invisible in Docker.** AGI recordings are written to
   `/dev/shm`; in the containerized stack the Asterisk container, the engine container and the host
   simulator each had their own `/dev/shm`, so the engine's ASR subprocess hit
   `FileNotFoundError: '/dev/shm/ai_audio_...wav'` and fell into the "I didn't hear anything" loop.
   Fix: bind-mount the host `/dev/shm` into both `asterisk` and `ivr-engine` in `docker-compose.yml`
   (`- /dev/shm:/dev/shm`). Verified on the host simulator too (it writes its `--ai-wav` into the
   same shared `/dev/shm`).
2. **`recognize_google` removed from newer SpeechRecognition.** The engine image installed the
   latest `SpeechRecognition` (3.17.0), whose `Recognizer` no longer has `recognize_google`
   (`AttributeError ... Did you mean: 'recognize_azure'?`). Fix in `IVR-engine/Dockerfile`:
   pin `SpeechRecognition==3.10.1` and install `typing_extensions` (required by the google
   recognizer, not pulled in with `--no-deps`).

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "I didn't hear anything. Let's try again." loop | ASR failing. Check `docker logs ivr-engine` for the Python ASR error; ensure `/dev/shm:/dev/shm` mount and `SpeechRecognition==3.10.1` + `typing_extensions` |
| `error.noresource: Failed to create implementation platform` | Expected non-fatal end-of-call JVoiceXML warning |
| "Returning cached VXML" after editing the file | Scenario cache — `docker compose restart ivr-engine` |
| `curl: 000` on the API option | Endpoint unreachable from the engine container (jsonplaceholder/coindesk are blocked on this network); `open-meteo` and `ipify` work |
| Transfer always unavailable | Ext `6001` is not registered; use a registered endpoint or `Local/<ext>@default/...` to test the `filled` branch |

## Key files

- `IVR-engine/scenarios/bilingual_demo.vxml` — the scenario
- `tools/agi_sim.py` — host AGI simulator (see `python3 tools/agi_sim.py --help`)
- `IVR-engine/Dockerfile`, `docker-compose.yml` — the two fixes above
- `IVR-engine/src/main/java/gov/iti/telecom/VxmlAgiHandler.java` — `<ai>`/`<record>`/ASR handling
- `/tmp/opencode/ai_balance.wav` — "balance" test utterance (16 kHz)
