# 04. IVR-Engine (FastAGI) Specification

## Service Overview & Telephony Execution Model
- **Runtime Environment**: Java 21 / Asterisk-Java 3.30.0 / JVoiceXML 0.7.7 Platform (Port `4573` TCP FastAGI)
- **Main Package**: `gov.iti.telecom`
- **Core Responsibilities**:
  - Serves incoming Asterisk FastAGI protocol requests on port `4573`.
  - Dynamically loads and caches exported VoiceXML 2.1 scenario documents (`.vxml`).
  - Executes VoiceXML dialogue state machines (prompts, DTMF/voice inputs, menus, subdialogs, transfers, recording, HTTP webhooks).
  - Interacts with Asterisk audio channels using custom JVoiceXML implementation platform (`AsteriskTelephony`, `AsteriskSpokenInput`, `AsteriskSynthesizedOutput`).
  - Synthesizes dynamic text-to-speech audio via `TtsEngine` (`espeak`/`festival` or pre-rendered WAV fallback).
  - Records granular call event logs into PostgreSQL `call_logs` and `call_events` tables upon call completion.

---

## Detailed Class Reference

### 1. FastAGI Server Listener & Request Handler

#### `FastAgiServerMain`
- **Responsibility**: Bootstraps the embedded Asterisk-Java `DefaultAgiServer` listener.
- **Public Methods**:
  - `main(String[] args)`: Initializes `DefaultAgiServer`, binds to port `4573`, loads mapping properties from `fastagi-mapping.properties`, and begins listening for Asterisk AGI socket connections.
- **Dependencies**: `Asterisk-Java DefaultAgiServer`, `fastagi-mapping.properties`

#### `VxmlAgiHandler` (Implements `AgiScript`)
- **Responsibility**: Main FastAGI script invoked by Asterisk when a channel executes `AGI(agi://127.0.0.1:4573/hello)`.
- **Execution Workflow**:
  1. Answers Asterisk channel (`answer()`).
  2. Reads Asterisk AGI variables:
     - `agi_callerid`: Caller telephone number or extension.
     - `agi_uniqueid`: Unique Asterisk call session ID.
     - `VXML_FILE` channel variable: Target VXML file name (e.g. `banking_iv`, `00000000-0000-0000-0000-000000000001_telecom_iv`).
  3. Invokes `VxmlLoader.loadVxml(vxmlName)` to retrieve VoiceXML XML document from `/app/scenarios/`.
  4. Validates document via `VxmlValidator.validate()`.
  5. Initializes `VxmlScenarioEngine` and `VxmlSession`.
  6. Executes VoiceXML dialogue nodes (plays audio prompts via `streamFile` or `ttsEngine`, captures DTMF digits via `getData`/`getOption`, handles transfers via `exec("Dial", ...)`).
  7. On channel hangup, calculates call duration and writes CDR record to `call_logs` and `call_events` tables via `CallLogDao`.
- **Dependencies**: `VxmlLoader`, `VxmlValidator`, `VxmlScenarioEngine`, `CallLogDao`, `AnalyticsTracker`

---

### 2. VoiceXML Loading, Validation & Engine Layer

#### `VxmlLoader`
- **Responsibility**: Loads VXML scenario files from `/app/scenarios` directory with in-memory caching.
- **Public Methods**:
  - `loadVxml(String scenarioName)`: Returns XML string content of scenario file. Checks exact file match, `.vxml` extension suffix, and tenant-prefixed scenario names.

#### `VxmlValidator`
- **Responsibility**: XML structure and VoiceXML compliance validator.
- **Public Methods**:
  - `validate(String vxmlContent)`: Parses XML string using DOM parser. Verifies root element is `<vxml>`, checks version attribute, validates form IDs, and ensures choice event handlers exist.

#### `VxmlScenarioEngine`
- **Responsibility**: Custom JVoiceXML engine wrapper executing VXML state transitions.
- **Public Methods**:
  - `execute(VxmlDocument doc, AgiChannel channel, AgiRequest request)`: Iterates through VoiceXML `<form>`, `<field>`, `<menu>`, `<choice>`, `<transfer>`, `<record>`, and `<subdialog>` elements, interacting with channel.

#### `VxmlSession`
- **Responsibility**: Holds active state variables, ECMAScript session scope, and field values during call execution.

---

### 3. Custom JVoiceXML Implementation Platform (`gov.iti.telecom.platform`)

- `AsteriskImplementationPlatformFactory`: JVoiceXML platform factory returning custom telephony, spoken input, and synthesized output components.
- `AsteriskTelephony`: Implements JVoiceXML `Telephony` interface. Controls Asterisk audio call channel (answer, hangup, transfer, bridge).
- `AsteriskSpokenInput`: Implements JVoiceXML `SpokenInput` interface. Handles DTMF digit collection and AGI recording (`<record>`).
- `AsteriskSynthesizedOutput`: Implements JVoiceXML `SynthesizedOutput` interface. Streams audio files (`streamFile()`) or triggers `TtsEngine` text-to-speech synthesis.

---

### 4. Utilities & Support Services

#### `TtsEngine`
- **Responsibility**: Synthesizes text-to-speech WAV files for dynamic prompts.
- **Public Methods**:
  - `synthesize(String text, String language)`: Invokes system binary (`espeak` / `festival`) or returns fallback pre-rendered audio prompt file path in `/var/lib/asterisk/sounds`.

#### `CallLogDao` & `AnalyticsTracker`
- **Responsibility**: Writes raw call execution events and duration statistics to PostgreSQL database upon call conclusion.

#### `SchoolApiServer`
- **Responsibility**: Embedded mock HTTP REST API server (Port `8080`) providing sample backend endpoints (`/api/student`, `/api/balance`) used by sample IVR scenarios for external `<data>` / `<subdialog>` webhooks.

#### `add_extension.sh` (Dialplan Provisioning Script)
- **Responsibility**: Shell script called by `IVR-AI-engine` when a tenant publishes an IVR flow.
- **Actions**:
  1. Receives arguments: `EXTENSION_NUMBER` (e.g. `1001`) and `VXML_SCENARIO_NAME` (e.g. `banking_iv`).
  2. Injects extension rule into `/etc/asterisk/extensions.conf`:
     ```ini
     exten => 1001,1,Answer()
     exten => 1001,n,Set(VXML_FILE=banking_iv)
     exten => 1001,n,AGI(agi://127.0.0.1:4573/default)
     exten => 1001,n,Hangup()
     ```
  3. Executes `asterisk -rx 'dialplan reload'` via Asterisk CLI.
