# IVR Platform (VXML & FastAGI)

This project is a dynamic Interactive Voice Response (IVR) platform built on Java and Asterisk. It uses **JVoiceXML** to parse industry-standard `.vxml` scripts and serves them to an Asterisk PBX dynamically over **FastAGI**. 

Because this architecture is fully dynamic, you **do not** need to recompile the Java application when adding new IVR scenarios.

---

## 🚀 1. Prerequisites

Before running the platform, ensure you have the following installed on your system:
* **Java 11+**
* **Maven** (`mvn`)
* **Asterisk PBX** (running locally on `127.0.0.1`)
* A SIP softphone (like Zoiper or MicroSIP) registered to your Asterisk server.

---

## 🛠️ 2. One-Time Asterisk Setup

To allow the Java environment and helper scripts to inject extensions and reload the dialplan automatically without requiring `sudo` every time, you must configure Asterisk to allow group socket connections.

Run the following commands once on your system:
```bash
sudo sed -i 's/^;astctlpermissions = 0660/astctlpermissions = 0660/' /etc/asterisk/asterisk.conf
sudo sed -i 's/^;astctlowner = root/astctlowner = asterisk/' /etc/asterisk/asterisk.conf
sudo sed -i 's/^;astctlgroup = apache/astctlgroup = asterisk/' /etc/asterisk/asterisk.conf
sudo chmod g+w /etc/asterisk/extensions.conf
sudo systemctl restart asterisk
```

---

## ▶️ 3. Running the Server

Start the FastAGI server. This Java process must be running in the background for Asterisk to route calls to the VXML engine.

```bash
cd IVR-engine
mvn clean compile
mvn exec:java -Dexec.mainClass="org.asteriskjava.fastagi.DefaultAgiServer"
```
You should see: `Listening on *:4573` and `Thread pool started.`

---

## 📞 4. Adding a New VXML Scenario

Adding a new IVR menu is incredibly simple and requires no Java code changes.

1. **Create the VXML file:**
   Place your new script (e.g., `my-scenario.vxml`) inside the `IVR-engine/scenarios/` directory.

2. **Map the Extension:**
   For the `add_extension.sh` script to write to the Asterisk configuration without requiring `sudo`, you must first make the file writable by your user account. Run this command once:
   ```bash
   sudo chown $USER:$USER /etc/asterisk/extensions.conf
   ```
   
   Then, use the provided helper script to inject the extension into Asterisk's dialplan. For example, to map extension `700` to `my-scenario.vxml`:
   ```bash
   cd IVR-engine
   ./add_extension.sh 700 my-scenario
   ```
   *(Note: The script automatically handles adding the `Answer()` command, setting the variables, injecting the FastAGI mapping into the `[default]` context, deleting old redundant entries, and reloading the dialplan!)*

3. **Call the Extension:**
   Open your SIP Softphone and dial `700`. Asterisk will instantly forward the call to the Java FastAGI server, which will parse your `.vxml`, synthesize the audio, and interact with the caller.

---

## 🤖 5. Conversational AI Routing

The platform supports a custom `<ai>` tag that allows callers to navigate menus using natural language instead of pressing buttons. The AI will converse with the caller, confirm their choice, and jump to the appropriate VXML form automatically!

**Example Usage:**
```xml
<ai role="You are a polite assistant." options="transfer:transfer_form, mobile balance:balance_form, complaint:complaint_form">
  <prompt>Welcome to our intelligent routing system. How can I direct your call today?</prompt>
</ai>
```
* `role`: Define the personality of the LLM.
* `options`: A comma-separated list of natural language intents mapped to their corresponding VXML `<form id="...">`.
* `<prompt>`: The initial greeting played to the user.

**Requirements:**
* Ollama running locally with the model `granite4.1:8b`.
* Python 3 with `SpeechRecognition` library (`pip3 install SpeechRecognition --break-system-packages`).

---

## 🎙️ 6. Custom Voice Recordings (`<audio>` Tag)

The platform supports the standard VXML `<audio>` tag, allowing you to replace TTS-generated prompts with your own pre-recorded voice files. This is essential for production IVR systems where you want a professional, consistent voice.

### Default Audio Storage Path

```
/var/lib/asterisk/sounds/ivr-custom/
```

> **⚠️ Webapp developers:** When the user uploads custom audio files through the web interface, they must be saved to this directory. The IVR engine resolves all `<audio src="...">` references against this path.

### Audio File Requirements

| Property | Recommended Value |
|----------|------------------|
| Format | WAV (`.wav`) |
| Sample Rate | 8000 Hz |
| Bit Depth | 16-bit |
| Channels | Mono (1 channel) |
| Other supported formats | `.gsm`, `.ulaw`, `.alaw`, `.sln`, `.mp3` |

You can convert any audio file to the correct format using `ffmpeg`:
```bash
ffmpeg -i input.mp3 -ar 8000 -ac 1 -codec:a pcm_s16le /var/lib/asterisk/sounds/ivr-custom/my_prompt.wav
```

### VXML Syntax

Use `<audio>` inside `<prompt>` tags. The text inside the tag is a **TTS fallback** — it plays via TTS if the audio file is not found:

```xml
<prompt>
  <audio src="welcome_en.wav">Welcome to our service. Press 1 for English.</audio>
  <audio src="welcome_ar.wav">أَهْلًا بِكَ. لِلْعَرَبِيَّةِ اضْغَطْ 2</audio>
</prompt>
```

**How it works:**
- ✅ File found in `/var/lib/asterisk/sounds/ivr-custom/` → plays the pre-recorded audio
- 🔄 File not found → falls back to TTS using the inner text (so it always works)
- 📞 DTMF-aware → callers can press digits during custom audio playback

You can also mix plain text with `<audio>` tags in the same prompt:
```xml
<prompt>
  <audio src="greeting.wav">Hello!</audio>
  Press 1 for sales, press 2 for support.
</prompt>
```

### Arabic Language Support

Both TTS and Speech Recognition support Arabic natively:
- **TTS:** Google TTS (`gTTS`) with language code `ar`
- **ASR:** Google Speech Recognition with locale `ar-EG`
- **Language routing:** Set via `<assign name="language" expr="'ar'"/>` in your VXML

---

## 📁 7. Directory Structure

* `IVR-engine/src/main/java/gov/iti/telecom/`: Core Java source code, VXML parsing, and Asterisk AGI bindings.
* `IVR-engine/scenarios/`: Drop your `.vxml` files here.
* `/var/lib/asterisk/sounds/ivr-tts/`: Auto-generated TTS audio cache (managed by the engine, do not edit).
* `/var/lib/asterisk/sounds/ivr-custom/`: **Place custom voice recordings here.** The webapp should upload audio files to this directory.
* `IVR-engine/add_extension.sh`: Bash utility for dynamically binding extensions to your VXML scenarios.
