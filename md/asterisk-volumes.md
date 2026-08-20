# Asterisk Volumes (asterisk-cdr, asterisk-sounds)

## Overview
These directories are host-mounted volumes used by the Asterisk Docker container to persist media and logs across container restarts.

## Technical Responsibilities
- **asterisk-sounds:** A shared volume where the backend AI engine writes dynamically generated Text-To-Speech (TTS) files and custom user-uploaded `.wav` prompts. Asterisk reads directly from this directory during call execution to stream audio back to the caller.
- **asterisk-cdr:** A volume for Asterisk's Call Detail Records (CSV files). Asterisk writes raw call logs here which can be ingested or backed up for auditing and billing verification.

## Architectural Flow
By utilizing Docker volume mounts, the platform achieves statelessness in the Asterisk container while maintaining necessary file-system communication between the Java IVR Engine (which downloads/generates audio) and the PBX Engine (which plays audio).
