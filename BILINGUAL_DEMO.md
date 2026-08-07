# Bilingual Demo Test Guide (`bilingual_demo.vxml`)

End-to-end test of the FastAGI VXML renderer with an Arabic/English IVR scenario.

## What it covers

The call starts with a **language selection** (`lang_select` menu, the engine always renders the
first `<menu>`): `1` = English, `2` = Arabic. It sets the session `language` variable and the main
menu is then spoken only in the chosen language (the menu is a `<form>` using `<if>` on
`${language}`). Subsequent prompts keep their `xml:lang` / session-language behavior.

| Menu digit | Scenario | Renderer features exercised |
|---|---|---|
| L (1/2) | Language select | First `<menu>` + `<choice>`, `<assign>` `language`, `<goto>` |
| 1 | Book | `field` + `grammar` (DTMF, `#`-terminated), `${var}` substitution |
| 2 | Account | `assign`/`var`, session `language` switch, `if`/`then`/`else`, `${var}` substitution |
| 3 | API | `api` (HTTP GET), dot-nested `jsonPath`, `saveResultAs`, spoken forecast, `field`→`filled`→`goto` back to the menu |
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

The first digit is always the language choice (`1` = English, `2` = Arabic), followed by the menu
digit. Digits are separated with `#` because the simulator sends keys back-to-back while the
`field` inter-digit window is 5s — a real caller just pauses (or presses `#`).

| digits | Expected |
|---|---|
| `1` | Language menu spoken in Arabic then English; `1` → English; `VXML_RESULT_user_choice=1` |
| `2` | `2` → Arabic; main menu greeting spoken in Arabic only |
| `1#1#2#` | EN menu → 1 (book) → field collects `2`; `VXML_RESULT_movie_selection=2`; `filled` runs → "You selected option 2. Booking confirmed. Goodbye." |
| `1#2#` | EN menu → 2 (account): Arabic session switch ("تغيير اللغة إلى العربية. رصيدك الحالي هو 50 جنيه"), `رصيدك منخفض.` (50 < 100 branch), `الحساب نشط.`, then back to English; `VXML_RESULT_balance=50`, `VXML_RESULT_status=active`, `VXML_RESULT_language=en` |
| `1#3#0#` | EN menu → 3 (API): live Cairo forecast via `api.open-meteo.com` (`latitude=30.0444&longitude=31.2357`): `VXML_RESULT_weather_temp=<°C>` (e.g. `27.4`), `VXML_RESULT_weather_code=0`; speaks "The current temperature in Cairo is 27.4 degrees Celsius" + condition ("The sky is clear.") in both languages, then "Press 0 to return to the main menu"; `0` (`VXML_RESULT_api_return=0`) `<goto>`s back to the main menu instead of ending the call |
| `1#4#` | EN menu → 4 (record): `VXML_RESULT_voice_msg` points to a `.wav` recording |
| `1#5#` | EN menu → 5 (AI): beep → ASR "balance" → Ollama `{"status":"FINAL","reply":...,"action":"balance_info"}` → "Jumping to dialog: balance_info" → bilingual balance prompt |
| `1#6#` | EN menu → 6 (transfer): `DIALSTATUS=CHANUNAVAIL` (6001 not registered) → catch branch "The agent is unavailable. Goodbye." |
| `1#7#` | EN menu → 7 (audio): engine log `Audio file not found, using TTS fallback for: welcome_custom.wav`; fallback text spoken in both languages |
| `2#0#` | AR menu → 0 (exit): "Thank you for calling. Goodbye." + Arabic goodbye, then hangup |

Engine logs (`docker logs ivr-engine --since 5m`) show `Loading VXML from file: /app/scenarios/bilingual_demo.vxml`, `Validation complete. Errors: 0`, and per-step `Speaking prompt: ...` lines.

## Run against real Asterisk

1. Publish `bilingual_demo` (or drop the file under `IVR-engine/scenarios/`).
2. If the scenario name was already loaded once, restart the engine first (the scenario cache has no invalidation):
   `docker compose restart ivr-engine`
3. The scenario is already wired to a SIP extension in the default dialplan: **dial `507`** from
   your SIP client (e.g. extension `1001`). It maps to `VXML_FILE=bilingual_demo` →
   `AGI(agi://127.0.0.1:4573/default)`. For a headless originate test:
   `docker exec ivr-asterisk asterisk -rx "channel originate Local/507@default/n extension 507@default"`

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

## Engine fixes required by this scenario (already applied)

1. **`<api>` `jsonPath` now supports dot-nested paths.** Previously only top-level fields were
   read, so the forecast's `current_weather.temperature` could not be extracted.
2. **`<field>` now runs its `<filled>` branch.** Previously a field stored the DTMF input but
   never executed `<filled>` (the booking confirmation and the return-to-menu `<goto>` were
   silently skipped).

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
