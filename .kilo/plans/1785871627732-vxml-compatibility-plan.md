# VXML Compatibility Plan: Webapp Output vs AGI Handler

## Goal

Determine whether the VXML produced by the webapp (`vxmlExporter.ts`) matches the VXML style the AGI handler (`VxmlAgiHandler.java`) understands and supports. If mismatches exist, modify the webapp's VXML output to ensure full compatibility.

---

## 1. Current State: VXML Produced by the Webapp

**File:** `IVR-webapp/src/ivr/vxmlExporter.ts`

The webapp generates VoiceXML 2.1 for each diagram block type:

| Block Type | VXML Output |
|---|---|
| start | `<form><block><goto next="#{target}"/></block></form>` |
| greeting | `<form><block><prompt><audio src="...">text</audio></prompt><goto/></block></form>` |
| playback | `<form><block><prompt><audio src="...">text</audio></prompt><goto/></block></form>` |
| tts | `<form><block><prompt>text</prompt><goto/></block></form>` |
| dtmf_menu | `<menu><prompt><audio>...</audio></prompt><choice dtmf="X" next="#...">label</choice><noinput><goto/></noinput><nomatch><prompt>...</prompt><reprompt/></nomatch></menu>` |
| dtmf_input | `<field name="..." type="digits"><prompt>...</prompt><filled><goto/></filled><noinput><goto/></noinput><nomatch><prompt>...</prompt><reprompt/></nomatch></field>` |
| queue | `<form><block><prompt>...</prompt><transfer name="..." dest="..." type="blind"><prompt>...</prompt></transfer></block><block><if cond="queue_result == 'answered'"><goto/><elseif cond="queue_result == 'busy' \|\| queue_result == 'noanswer"/><goto/><else/><goto/></if></block></form>` |
| transfer | `<form><block><prompt>...</prompt><transfer name="..." dest="..." type="bridge"><prompt>...</prompt></transfer></block><block><if cond="xfer_result == 'transferred'"><goto/><else/><prompt>...</prompt><goto/></if></block></form>` |
| extension | `<form><block><transfer name="..." dest="..." type="blind"/></block><block><if cond="ext_result == 'answered'"><goto/><else/><goto/></if></block></form>` |
| voicemail | `<form><record name="..." beep="true" maxtime="120s" finalsilence="4s" dtmfterm="true" dest="..."><prompt>...</prompt></record><block><prompt>...</prompt><goto/></block></form>` |
| record | `<form><block><!-- comment --><goto/></block></form>` |
| hours | `<form><block><if cond="true /* TODO */"><goto/><else/><goto/></if></block></form>` |
| holiday | `<form><block><if cond="false /* TODO */"><goto/><else/><goto/></if></block></form>` |
| condition | `<form><block><if cond="true /* TODO */"><goto/><else/><goto/></if></block></form>` |
| variable | `<form><block><assign name="..." expr="'' /* TODO */"/><goto/></block></form>` |
| api | `<form><block><data name="..." src="..." method="post"/></block><block><if cond="api_result.status == 'success'"><goto/><else/><goto/></if></block></form>` |
| database | `<form><block><data name="..." src="..." method="get"/></block><block><if cond="db_result != null"><goto/><else/><goto/></if></block></form>` |
| webhook | `<form><block><submit next="..." method="post" namelist="..."/></block><block><if cond="true"><goto/><else/><goto/></if></block></form>` |
| ai | `<form><field name="ai_intent" type="string"><grammar src="ai_grammar.grxml" type="application/srgs+xml"/><prompt>...</prompt><filled><if cond="ai_intent != null &amp;&amp; ai_intent != ''"><goto/><else/><goto/></if></filled><noinput><goto/></noinput></field></form>` |
| end | `<form><block><prompt>...</prompt><disconnect/></block></form>` |

**Root element:** `<vxml version="2.1" xmlns="http://www.w3.org/2001/vxml" xmlns:xsi="..." xsi:schemaLocation="..." application="root.vxml">`

**Global variables:** `<var name="session_id" expr="session.sessionid"/>` etc. at the top level.

**Metadata:** `<meta name="flowname" content="..."/>` at the top level.

---

## 2. Current State: VXML Tags the AGI Understands

**File:** `IVR-engine/src/main/java/gov/iti/telecom/VxmlAgiHandler.java`

The AGI handler (911 lines) processes VXML documents via JVoiceXML + Asterisk FastAGI. It explicitly handles these tags:

### Standard VoiceXML tags handled:
- `<vxml>`, `<form>`, `<block>`, `<prompt>`, `<goto>`
- `<menu>`, `<choice>`
- `<field>` (with DTMF digit collection)
- `<grammar>` (not explicitly parsed but tolerated)
- `<filled>`, `<noinput>`, `<nomatch>`
- `<transfer>` (with bridge/blind types)
- `<disconnect>`
- `<audio>` (inside `<prompt>`)
- `<if>`, `<elseif>`, `<else>`
- `<reprompt>`
- `<data>` (tolerated but not actively processed)
- `<submit>` (tolerated but not actively processed)

### Custom tags handled by the AGI:
- **`<assign name="..." expr="..."/>`** — Sets session variables. Handles single-quoted string expressions.
- **`<api url="..." var="..." saveResultAs="..." jsonPath="..."/>`** — Makes HTTP GET requests, parses JSON response, saves result to session variable.
- **`<ai role="..." options="...">`** — AI interaction node. Records audio, sends to Ollama for ASR+LLM, routes based on JSON response with `status` (CONFIRMING/FINAL) and `action` (destination ID).

### Tags the AGI does NOT handle:
- `<record>` — No handler in `renderFormElement()` or `renderBlockElement()`
- `<var>` — No handler at the form or document level
- `<meta>` — No handler
- `<grammar>` inside `<field>` — Not explicitly parsed (the AGI's `<field>` handler only processes `<prompt>` children, not `<grammar>`)

---

## 3. Compatibility Analysis

### 3.1 Tags the webapp produces that the AGI DOES handle

| Webapp Tag | AGI Support | Notes |
|---|---|---|
| `<vxml>` | Yes | Root element |
| `<form>` | Yes | Form container |
| `<block>` | Yes | Block container |
| `<prompt>` | Yes | With `<audio>` sub-tag support |
| `<goto>` | Yes | Navigation |
| `<menu>` | Yes | DTMF menu with `<choice>` |
| `<choice>` | Yes | DTMF choice with dtmf/next |
| `<field>` | Yes | DTMF input field |
| `<filled>` | Yes | Input handler |
| `<noinput>` | Yes | Timeout handler |
| `<nomatch>` | Yes | Invalid input handler |
| `<transfer>` | Yes | With type="blind" and type="bridge" |
| `<disconnect>` | Yes | End call |
| `<audio>` | Yes | Inside `<prompt>` |
| `<if>` / `<elseif>` / `<else>` | Yes | Conditional branching |
| `<reprompt>` | Yes | Inside `<nomatch>` |
| `<assign>` | Yes | Variable assignment |
| `<data>` | Partial | Tolerated but not actively processed |
| `<submit>` | Partial | Tolerated but not actively processed |

### 3.2 Tags the webapp produces that the AGI does NOT handle

| Webapp Tag | Used By | AGI Support | Severity |
|---|---|---|---|
| `<record>` | voicemail node | **NOT handled** | **High** — voicemail nodes will fail |
| `<var>` | global variables (top-level) | **NOT handled** | Medium — variables silently ignored |
| `<meta>` | metadata (top-level) | **NOT handled** | Low — informational only |
| `type="string"` on `<field>` | ai node | **Not handled** — AGI expects `<ai>` tag, not `<field type="string">` | **High** — AI nodes will not work |
| `<grammar>` inside `<field>` | ai node, dtmf_input | **Not explicitly parsed** — AGI's `<field>` handler only processes `<prompt>` children | Medium — grammar-based input won't work |
| `type="digits"` on `<field>` | dtmf_input | **Partially handled** — AGI collects digits but doesn't use the `type` attribute | Low — works but non-standard |

### 3.3 Tags the AGI handles that the webapp does NOT produce

| AGI Tag | Webapp Equivalent | Gap |
|---|---|---|
| `<api url="..." var="..." saveResultAs="..."/>` | `<data>` + `<submit>` | Webapp uses standard VoiceXML `<data>`/`<submit>` instead of AGI's custom `<api>` tag |
| `<ai role="..." options="...">` | `<field type="string">` with `<grammar>` | Completely different approach — webapp uses standard `<field>`, AGI expects custom `<ai>` |

### 3.4 Identified Mismatches (Summary)

1. **`<record>` for voicemail** — The webapp produces `<record>` inside `<form>`. The AGI handler has no code to process `<record>` elements. Voicemail nodes will silently fail at runtime.

2. **AI node uses `<field type="string">`** — The webapp produces a standard `<field>` with `<grammar>` for AI nodes. The AGI expects a custom `<ai>` tag with `role` and `options` attributes that triggers Ollama-based conversation.

3. **API/Webhook nodes use `<data>` and `<submit>`** — The webapp uses standard VoiceXML `<data>` and `<submit>` for API/webhook nodes. The AGI expects a custom `<api>` tag that handles HTTP requests with variable substitution and JSON path extraction.

4. **`<var>` and `<meta>` at top level** — The webapp includes `<var>` and `<meta>` elements in the `<vxml>` root. The AGI handler does not process these.

5. **`type="digits"` on `<field>`** — The webapp uses `type="digits"` which is non-standard (standard VoiceXML uses `type="digits"` but AGI may interpret it differently).

---

## 4. Execution Plan

### Phase 1: Modify Webapp VXML Output for AGI Compatibility

**Target:** `IVR-webapp/src/ivr/vxmlExporter.ts`

**Tasks:**
1. **Voicemail node** — Replace `<record>` with a `<block>` containing a `<prompt>` and `<goto>`, OR add `<record>` handling to the AGI handler (see Phase 2).
2. **AI node** — Replace `<field type="string">` with `<ai role="..." options="...">` tag matching the AGI's expected format.
3. **API node** — Replace `<data>`/`<submit>` with `<api url="..." var="..." saveResultAs="..."/>` tag matching the AGI's expected format.
4. **Webhook node** — Replace `<submit>` with `<api>` tag or keep `<submit>` if AGI tolerates it.
5. **Remove top-level `<var>` and `<meta>`** — These are not handled by the AGI.
6. **Fix `type="digits"`** — Use standard `type="digits"` or remove the `type` attribute.

### Phase 2: Extend VxmlAgiHandler.java for Missing Tags

**Target:** `IVR-engine/src/main/java/gov/iti/telecom/VxmlAgiHandler.java`

**Tasks:**
1. **Add `<record>` handler** — In `renderFormElement()`, add a case for `"record"` that handles voicemail recording (similar to how `<ai>` handles audio recording).
2. **Add `<var>` handler** — In `renderFormElement()`, add a case for `"var"` that sets session variables from `expr` attribute.
3. **Add `<meta>` handler** — Log or ignore `<meta>` tags (informational).
4. **Add `<grammar>` parsing in `<field>`** — In the `<field>` handler, parse `<grammar>` children to extract DTMF tokens.

### Phase 3: Add Compatibility Tests

**Target:** New test file in `IVR-engine/src/test/java/gov/iti/telecom/`

**Tasks:**
1. Create `VxmlAgiCompatibilityTest.java`
2. For each block type, test that the VXML produced by `vxmlExporter.ts` can be parsed and executed by `VxmlAgiHandler`
3. Test edge cases: empty prompts, missing targets, malformed conditions

---

## 5. Risk Assessment

| Risk | Impact | Mitigation |
|---|---|---|
| `<record>` not handled by AGI | High — voicemail nodes fail | Add `<record>` handler to AGI or change webapp output |
| AI node uses wrong tag format | High — AI nodes fail | Change webapp to use `<ai>` tag |
| API node uses `<data>`/`<submit>` | Medium — API calls may not work | Change webapp to use `<api>` tag |
| `<var>`/`<meta>` not handled | Low — informational only | Remove from webapp output or add AGI handler |
| `type="digits"` non-standard | Low — works but non-standard | Use standard `type="digits"` or remove |

---

## 6. Validation Steps

1. Run VXML export from the webapp for each block type
2. Validate the output against the AGI handler's expected tag set
3. Run existing `VxmlAgiHandler` tests (if any)
4. Verify `VxmlParser.java` in IVR-AI-engine can round-trip the modified VXML back to `FlowModel`
5. Test voicemail, AI, and API nodes end-to-end with the AGI handler

---

## 7. Open Questions

1. **Should `<record>` be handled by adding code to the AGI handler, or should the webapp avoid producing it?** — Adding AGI support is more robust; the webapp should produce standard-compliant VXML.
2. **Should the AI node use `<ai>` or `<field>`?** — The AGI handler explicitly supports `<ai>` with Ollama integration. The webapp should use `<ai>`.
3. **Should the API node use `<api>` or `<data>`/`<submit>`?** — The AGI handler supports `<api>` with JSON path extraction. The webapp should use `<api>`.
4. **What is the `ai_grammar.grxml` file referenced by the webapp's AI node?** — This file doesn't exist in the project. The AGI handler doesn't reference it either.
