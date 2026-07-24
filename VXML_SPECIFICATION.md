# VoiceXML 2.1 (VXML) Standard Architecture & Integration Guide

## Overview
NexusIVR supports the W3C VoiceXML 2.1 (VXML) standard as its primary scenario format for IVR flow interchange between the Web UI (`IVR-webapp`) and the FastAGI Telephony Engine (`IVR-engine`).

---

## 1. Web UI VoiceXML Integration (`IVR-webapp`)
The Web UI now utilizes VoiceXML 2.1 standard format (`.vxml`) instead of raw JSON:

- **VXML Generator (`vxmlGenerator.ts`)**: Automatically translates flow graph canvas nodes & edges into valid W3C VoiceXML 2.1 standard XML documents.
- **VXML Parser (`vxmlParser.ts`)**: Parses standard `<vxml>`, `<form>`, `<menu>`, `<prompt>`, `<field>`, `<transfer>`, and `<choice>` tags from uploaded `.vxml` files to reconstruct canvas nodes & edges.
- **VXML Editor & Viewer (`VxmlModal.tsx`)**: Provides live VXML code view, copy, file download (`.vxml`), and direct code editing to apply changes back to the canvas.
- **Validation Engine (`vxmlValidator.ts`)**: Enforces VXML 2.1 compliance rules (proper document root, non-empty DTMF menus, destination URIs).

---

## 2. Telephony Engine Integration (`IVR-engine`)
The Java FastAGI engine parses and interprets VoiceXML dialogs dynamically over Asterisk:

- **`VxmlDocument` AST**: Object model representing VoiceXML `<vxml>`, `<form>`, `<menu>`, `<field>`, `<choice>`, `<transfer>`, `<disconnect>`.
- **`VxmlParser`**: DOM XML parser for VoiceXML documents using standard `javax.xml.parsers`.
- **`VxmlScenarioLoader`**: Thread-safe cache loading `.vxml` and `.xml` scenarios from the `scenarios/` directory.
- **`VxmlInterpreter`**: Executes VoiceXML dialogs via Asterisk FastAGI (`streamFile`, `getData`, `exec("Dial")`, `hangup`).

---

## 3. Supported VoiceXML 2.1 Standard Tags

| VoiceXML Tag | Description | Canvas Mapping |
| :--- | :--- | :--- |
| `<vxml version="2.1">` | Root XML Document | Full Flow Document |
| `<form id="...">` | Form Dialog | Playback / Input / Transfer / End Node |
| `<menu id="...">` | Choice Menu Dialog | DTMF Menu Node |
| `<prompt>` / `<audio>` | Text-To-Speech or WAV prompt | Prompt Audio / Subtitle |
| `<field name="...">` | Digit input collection | DTMF Input Node |
| `<choice dtmf="..." next="...">` | Keypad DTMF branch | Canvas Output Edge |
| `<transfer dest="...">` | SIP Call Transfer | Transfer / Queue Node |
| `<disconnect/>` / `<exit/>` | Terminate call | End Call Node |

---

## 4. Asterisk Dialplan Configuration

To route incoming telephony calls to a VoiceXML scenario, configure your `/etc/asterisk/extensions.conf`:

```ini
[default]
; Execute restaurant-booking-001.vxml scenario
exten => 100,1,Answer()
exten => 100,2,AGI(agi://127.0.0.1:4573/restaurant-booking-001)
exten => 100,3,Hangup()

; Execute banking-service-003.vxml scenario
exten => 200,1,Answer()
exten => 200,2,AGI(agi://127.0.0.1:4573/banking-service-003)
exten => 200,3,Hangup()
```

---

## 5. Pushing Commits to Remote Branch

To push all 15 commits to the remote `Feat/VXML` branch:

```bash
git push -u origin Feat/VXML
```
