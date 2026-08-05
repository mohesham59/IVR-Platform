# IVR Platform (VXML & FastAGI) & NexusIVR AI Builder

This project is a dynamic Interactive Voice Response (IVR) platform built on Java and Asterisk. It uses **JVoiceXML** to parse industry-standard `.vxml` scripts and serves them to an Asterisk PBX dynamically over **FastAGI**. 

Additionally, this repository contains the **NexusIVR AI Builder**, an AI-powered Web Platform that allows you to instantly generate, visualize, edit, and deploy full IVR VoiceXML flows using natural language.

---

## 🚀 1. Prerequisites

Before running the platform, ensure you have the following installed on your system:
* **Java 21+**
* **Maven** (`mvn`)
* **Node.js 18+** & npm (for the React Frontend)
* **Asterisk PBX** (running locally on `127.0.0.1`)
* A SIP softphone (like Zoiper or MicroSIP) registered to your Asterisk server.

---

## 🛠️ 2. One-Time Asterisk & System Setup

To allow the Java backend to inject extensions and reload the dialplan automatically without requiring manual `sudo` intervention on every publish, you must configure a `sudoers` exception for the helper script.

Run the following command **once** on your system:
```bash
echo "$USER ALL=(ALL) NOPASSWD: /bin/bash /home/mohamed/IdeaProjects/IVR_project/IVR_platform/IVR-engine/add_extension.sh *" | sudo tee /etc/sudoers.d/nexus_ivr
```
*(Note: The `*` at the end is absolutely critical to allow the backend to pass the extension number and business name as arguments to the script without triggering a password prompt).*

You must also configure Asterisk to allow group socket connections:
```bash
sudo sed -i 's/^;astctlpermissions = 0660/astctlpermissions = 0660/' /etc/asterisk/asterisk.conf
sudo sed -i 's/^;astctlowner = root/astctlowner = asterisk/' /etc/asterisk/asterisk.conf
sudo sed -i 's/^;astctlgroup = apache/astctlgroup = asterisk/' /etc/asterisk/asterisk.conf
sudo chmod g+w /etc/asterisk/extensions.conf
sudo systemctl restart asterisk
```

---

## ▶️ 3. How to Run the Project (Full Stack)

To run the complete AI-powered visual IVR builder, you need to start three components:

### A. The FastAGI IVR Engine (Asterisk Bridge)
This Java process must be running in the background for Asterisk to route calls to the VoiceXML engine.
```bash
cd IVR-engine
mvn clean compile
mvn exec:java -Dexec.mainClass="org.asteriskjava.fastagi.DefaultAgiServer"
```
*(You should see: `Listening on *:4573` and `Thread pool started.`)*

### B. The NexusIVR AI Backend
This powers the AI flow generation, VXML parsing, validation, and automated publishing to Asterisk.
```bash
cd IVR-AI-engine
mvn clean package cargo:run -Dmaven.test.skip=true
```
*(This starts the Tomcat server on `http://localhost:8081`)*

### C. The React Web App (Frontend)
This provides the beautiful drag-and-drop IVR canvas and AI chat interface.
```bash
cd IVR-webapp
npm install
npm run dev
```
*(Open `http://localhost:5173` in your browser)*

---

## 🧠 4. How It Works (The AI Flow Pipeline)

The workflow for generating and deploying an IVR system with AI works seamlessly across the stack:

1. **AI Generation (Frontend -> Backend -> LLM):** You type a prompt in the webapp (e.g. "Build a banking IVR"). The backend calls the LLM, which writes valid VoiceXML.
2. **Parsing & Visualization:** The backend parses the AI's VoiceXML (`VxmlToModelConverter`) into a JSON model, carefully extracting the spoken text (`prompt`) and node connections. This is sent to the frontend, which renders it into interactive, draggable React Flow nodes.
3. **User Editing:** The user can tweak nodes, fix connections, or rename prompts directly in the visual editor.
4. **Validation & Publishing:** When the user clicks **Publish**, the frontend sends the graph JSON back. The backend validates it (e.g. blocking "dead end" nodes) and exports it into a pristine `.vxml` file in the `scenarios/` directory.
5. **Asterisk Registration:** Finally, the backend triggers `add_extension.sh` (using the passwordless `sudoers` rule) to register the new IVR in `/etc/asterisk/extensions.conf` and reloads the dialplan automatically!

---

## 📞 5. Manually Adding a VXML Scenario

If you don't want to use the AI Builder, adding a new IVR menu manually is still incredibly simple:

1. Place your new script (e.g., `my-scenario.vxml`) inside the `IVR-engine/scenarios/` directory.
2. Use the helper script to inject the extension into Asterisk's dialplan (e.g. extension `700`):
   ```bash
   cd IVR-engine
   sudo /bin/bash add_extension.sh 700 my-scenario
   ```
3. Open your SIP Softphone and dial `700`.

---

## 🎙️ 6. Custom Voice Recordings (`<audio>` Tag)

The platform supports the standard VXML `<audio>` tag, allowing you to replace TTS-generated prompts with your own pre-recorded voice files. 

### Default Audio Storage Path
```
/var/lib/asterisk/sounds/ivr-custom/
```
> **⚠️ Webapp developers:** When the user uploads custom audio files through the web interface, they must be saved to this directory. The IVR engine resolves all `<audio src="...">` references against this path.

**How it works:**
Use `<audio>` inside `<prompt>` tags. The text inside the tag acts as a **TTS fallback** if the audio file is missing.
```xml
<prompt>
  <audio src="welcome_en.wav">Welcome to our service. Press 1 for English.</audio>
</prompt>
```

---

## 📁 7. Directory Structure

* `IVR-engine/`: Core Java FastAGI server, VXML parsing, and Asterisk AGI bindings.
* `IVR-engine/scenarios/`: Drop your `.vxml` files here (the AI builder publishes them here automatically).
* `IVR-engine/add_extension.sh`: Bash utility for dynamically binding extensions to your VXML scenarios.
* `IVR-AI-engine/`: The NexusIVR Backend (REST APIs, LLM Prompts, Validation, and VXML translation).
* `IVR-webapp/`: The React + Vite frontend for the AI Assistant and drag-and-drop Visual Flow Builder.
* `/var/lib/asterisk/sounds/ivr-custom/`: Place custom `.wav` voice recordings here.
