# 09. Infrastructure, Docker & Environment Configuration

## Multi-Container Architecture Overview

NexusIVR uses Docker Compose (`docker-compose.yml`) for local development and production container orchestration. The setup consists of 5 main containerized services running on Linux with host networking for VoIP/RTP traffic.

---

## Service Containers & Docker Specification

### 1. `asterisk` (PBX Telephony Server)
- **Image**: `andrius/asterisk:latest`
- **Network Mode**: `host` (Ensures RTP audio ports 10000-20000 and SIP ports 5060/5061 pass directly without Docker NAT latency)
- **Volume Mounts**:
  - `/etc/asterisk`: Shared Asterisk PBX dialplan (`extensions.conf`), SIP endpoints (`pjsip.conf`), AMI configuration.
  - `/var/run/asterisk`: UNIX socket directory for Asterisk control (`asterisk.ctl`, `asterisk.pid`).
  - `./asterisk-sounds:/var/lib/asterisk/sounds`: Shared audio directory for uploaded/generated prompt files.
  - `/dev/shm`: Shared host memory for fast AGI audio call recordings.
  - `./asterisk-cdr:/var/log/asterisk/cdr-csv`: Shared CDR output directory (`Master.csv`).

### 2. `ivr-engine` (FastAGI Scenario Execution Engine)
- **Build Context**: `./IVR-engine`
- **Network Mode**: `host` (Allows Asterisk to connect via `agi://127.0.0.1:4573`)
- **Environment Variables**:
  - `OLLAMA_MODEL`: Default local AI model (`granite4.1:8b`).
  - `DATABASE_URL`: PostgreSQL JDBC connection URL.
  - `DATABASE_USER`: Database username (`neondb_owner`).
  - `DATABASE_PASSWORD`: Database password.
- **Volume Mounts**:
  - `./IVR-engine/scenarios:/app/scenarios`: Live VXML scenarios folder.
  - `./asterisk-sounds:/var/lib/asterisk/sounds`: Shared sounds folder.
  - `/dev/shm`: Shared host RAM drive for audio recordings.

### 3. `ivr-ai-engine` (Core REST API Backend)
- **Build Context**: `./IVR-AI-engine`
- **Port Mapping**: `8081:8081`
- **Env Files**: `./IVR-AI-engine/.env`
- **Environment Variables**:
  - `OLLAMA_BASE_URL`: `http://host.docker.internal:11434`
  - `DATABASE_URL`: PostgreSQL connection string with SSL (`sslmode=require`).
  - `DATABASE_USER`, `DATABASE_PASSWORD`: Database credentials.
  - `AMI_HOST`: `host.docker.internal`
  - `AI_PROVIDER`: `openrouter` (or `gemini`, `groq`, `ollama`).
  - `OPENROUTER_API_KEY`, `OPENROUTER_BASE_URL`, `OPENROUTER_MODEL`
  - `GEMINI_API_KEY`, `GROQ_API_KEY`
- **Healthcheck**: `curl -f http://localhost:8081/nexusivr-ai-engine/health || exit 1` (5s interval, 20 retries).

### 4. `ivr-payment-service` (Paymob Payment Service)
- **Build Context**: `./IVR-payment-service`
- **Port Mapping**: `8082:8082`
- **Env Files**: `./IVR-payment-service/.env`
- **Environment Variables**:
  - `PAYMENT_SERVICE_PORT`: `8082`
  - `PAYMOB_API_KEY`, `PAYMOB_SECRET_KEY`, `PAYMOB_PUBLIC_KEY`
  - `PAYMOB_HMAC_SECRET`: Secret key for SHA-512 callback verification.
  - `PAYMOB_INTEGRATION_ID_CARD`: Card integration ID (`5834828`).
  - `PAYMOB_IFRAME_ID`: Payment iframe ID (`1067447`).
- **Healthcheck**: `curl -f http://localhost:8082/nexusivr-payment-service/health || exit 1` (5s interval, 15 retries).

### 5. `ivr-webapp` (Frontend Web Application)
- **Build Context**: `./IVR-webapp` (Nginx + static Vite build bundle)
- **Port Mapping**: `3000:80`
- **Dependencies**: `ivr-ai-engine` (`service_healthy`), `ivr-payment-service` (`service_healthy`).

---

## Environment Variable Cross-Check Inventory Table

| Variable Name | Code Location Referenced | `docker-compose.yml` Status | `.env` Status | Description |
| :--- | :--- | :--- | :--- | :--- |
| `DATABASE_URL` | `DatabaseManager.java` | Present | Present | JDBC connection string to PostgreSQL |
| `DATABASE_USER` | `DatabaseManager.java` | Present | Present | PostgreSQL DB username |
| `DATABASE_PASSWORD` | `DatabaseManager.java` | Present | Present | PostgreSQL DB password |
| `AI_PROVIDER` | `GlobalAiConfig.java` | Present | Present | Active AI provider key (`openrouter`, `gemini`, `groq`, `ollama`) |
| `OPENROUTER_API_KEY` | `OpenAiCompatibleClient.java` | Passed via `.env` | Present | API Key for OpenRouter service |
| `OPENROUTER_BASE_URL` | `OpenAiCompatibleClient.java` | Passed via `.env` | Present | API Endpoint URL for OpenRouter |
| `OPENROUTER_MODEL` | `OpenAiCompatibleClient.java` | Passed via `.env` | Present | Model identifier (`openai.gpt-oss-20b-1:0`) |
| `GEMINI_API_KEY` | `GeminiClient.java` | Passed via `.env` | Present | API Key for Google Gemini |
| `GROQ_API_KEY` | `GroqClient.java` | Passed via `.env` | Present | API Key for Groq Cloud |
| `OLLAMA_BASE_URL` | `OllamaClient.java` | Present | Present | Local Ollama endpoint |
| `AMI_HOST` | `AsteriskAmiClient.java` | Present | N/A | Asterisk Manager Interface host |
| `PAYMOB_API_KEY` | `PaymobConfig.java` | Passed via `.env` | Present | Paymob API Token credential |
| `PAYMOB_HMAC_SECRET` | `HmacVerifier.java` | Passed via `.env` | Present | Secret key for SHA-512 callback verification |
| `PAYMOB_INTEGRATION_ID_CARD`| `PaymobConfig.java` | Passed via `.env` | Present | Paymob card payment integration ID |
| `PAYMOB_IFRAME_ID` | `PaymobConfig.java` | Passed via `.env` | Present | Paymob hosted checkout iframe ID |

---

## Startup Sequence & Boot Dependencies

1. **PostgreSQL Database** (Remote NeonDB cloud instance initialized & verified).
2. **`asterisk` PBX Container**: Starts Asterisk process, mounts audio volumes and CDR directories.
3. **`ivr-engine` FastAGI Container**: Starts JVoiceXML FastAGI listener on TCP port `4573`.
4. **`ivr-payment-service` Container**: Starts Tomcat on port `8082`, verifies Paymob keys, starts 24-hour expiration scheduler.
5. **`ivr-ai-engine` Container**: Starts Tomcat on port `8081`, verifies PostgreSQL connection, probes Asterisk AMI socket, executes liveness healthcheck.
6. **`ivr-webapp` Container**: Waits for `ivr-ai-engine` and `ivr-payment-service` to pass healthchecks (`service_healthy`), then starts Nginx on port `3000`.
