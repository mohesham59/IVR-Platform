# VXML Testing Guide - Using Zoiper SIP Client

**Date**: 2026-07-24  
**Objective**: Test IVR VXML scenarios using Asterisk + Zoiper  
**Estimated Time**: 15-20 minutes setup, then real-time testing

---

## 🎯 Quick Start (5 minutes)

### Prerequisites
- ✅ Asterisk running (`systemctl status asterisk`)
- ✅ Java IVR platform running (`mvn exec:java` or as daemon)
- ✅ Zoiper installed on phone/computer
- ✅ VXML files in `scenarios/` directory

### Quick Test
1. **Open Zoiper** → Login as extension 1001 (password: 1234)
2. **Dial 500** → Should hear "Hello. Welcome to the IVR system."
3. **Done!** You've tested your first VXML scenario

---

## 🔧 Setup (Detailed Steps)

### Step 1: Verify Asterisk is Running

```bash
# Check Asterisk status
systemctl status asterisk

# Expected: active (running)

# Or start it manually
sudo systemctl start asterisk

# Check it's listening on SIP port
netstat -tlnp | grep :5060
# Expected: tcp  0  0 0.0.0.0:5060  0.0.0.0:*  LISTEN

# Verify FastAGI port
netstat -tlnp | grep :4573
# Expected: tcp  0  0 127.0.0.1:4573  0.0.0.0:*  LISTEN
```

### Step 2: Verify Asterisk Extensions Configuration

```bash
# Check extensions.conf was updated
cat /etc/asterisk/extensions.conf | grep "exten => 500"
# Should show VXML IVR entries

# Reload dialplan in Asterisk CLI
asterisk -r -x "dialplan reload"

# Verify dialplan loaded
asterisk -r -x "dialplan show default" | grep 500
```

### Step 3: Verify Java IVR Platform is Running

```bash
# In terminal 1: Build the project
cd IVR-engine
mvn clean compile

# In terminal 2: Start the FastAGI server
# Option A: Run directly
java -cp target/classes gov.iti.telecom.App &

# Option B: Run as Maven project (if App has main method)
mvn exec:java -Dexec.mainClass="gov.iti.telecom.App"

# Expected output:
# [VxmlLoader] Initialized with resource path: scenarios/
# [VxmlScenarioEngine] Engine initialized
# [FastAGI] Listening on port 4573

# Check FastAGI server is listening
ps aux | grep java | grep -i agi
ss -tlnp | grep 4573
```

### Step 4: Set Up Zoiper (SIP Client)

#### On Android/iOS:
1. Install Zoiper from Google Play Store or App Store
2. Open Zoiper → Create new account
3. Server: `192.168.x.x` (your Asterisk server IP)
4. Username: `1001`
5. Password: `1234`
6. Save and login

#### On PC/Mac:
1. Download from [zoiper.com](https://www.zoiper.com)
2. Install and run
3. Settings → Accounts → Add
4. Server: `192.168.x.x`
5. Username: `1001`
6. Password: `1234`
7. Click "Save"

**Test connection**: Call 1002 (another extension)
- If you hear ringing, Asterisk connection is working ✓

---

## 📞 Testing VXML Scenarios

### Test 1: Simple Hello VXML (Extension 500)

**Dial**: 500  
**Expected Behavior**:
1. Call connects
2. Audio plays: "Hello. Welcome to the IVR system."
3. Call ends after message

**What's Happening**:
```
Phone (1001) → Asterisk → FastAGI → VxmlAgiHandler → VxmlLoader 
→ hello.vxml → JVoiceXML → TTS → Audio back to phone
```

**Debug if it fails**:
```bash
# Check Asterisk logs
tail -f /var/log/asterisk/messages
# Look for: "VXML IVR: Hello World" and AGI execution

# Check Java app logs
# Look for: "[VxmlAgiHandler] VXML selected: hello"

# If no audio: Check TTS provider (Google Cloud credentials)
# If call hangs: Check timeout settings in VxmlConfig.properties
```

---

### Test 2: Interactive Menu VXML (Extension 501)

**Dial**: 501  
**Expected Behavior**:
1. Call connects
2. Audio plays menu prompt (try to ask for input)
3. After ~3-5 seconds, press a DTMF key:
   - Press **1** → Should hear "You selected reservations"
   - Press **2** → Should hear "You selected information"
   - Press **0** → Should transfer/hang up (operator)
   - Other keys → "Invalid choice"
4. Call ends

**DTMF (Tone) Input in Zoiper**:
- While call is active, press number keys on phone
- Zoiper will send DTMF tones to Asterisk
- Listen for confirmation audio

**Variables Collected**:
After call ends, Asterisk has these variables:
- `VXML_SESSION_ID=1001_1719239400_1234`
- `VXML_STATE=COMPLETED`
- `VXML_RESULT_user_choice=1` (or 2, 0, etc.)

**Debug if DTMF not working**:
```bash
# In Asterisk CLI
asterisk -r
# Check if DTMF was received
> core show calls

# Enable verbose logging
> logger level core debug

# Try pressing DTMF again and check output
```

---

### Test 3: Transfer/Chaining VXML (Extension 502)

**Dial**: 502  
**Expected Behavior**:
1. Call connects
2. Audio: "Transferring you to the next menu."
3. VXML transfers to menu-example.vxml
4. Menu prompt plays
5. You can select options (DTMF 1, 2, 0)

**What's Being Tested**: VXML chaining (transfer from one .vxml to another)

**Flow**:
```
transfer-example.vxml (greeting) 
→ transfer element (automatic redirect)
→ menu-example.vxml (interactive menu)
```

---

### Test 4: Complex Form VXML (Extension 503)

**Dial**: 503  
**Expected Behavior**:
1. Call connects
2. Restaurant booking prompt plays
3. System asks for date (MMDD format)
4. System asks for time (HHMM format)
5. System asks for party size (1-2 digits)
6. After each input, confirmationAudio repeats/confirms

**DTMF Input Examples**:
```
Prompt: "Enter date MMDD"
You press: 7, 2, 4, (pause)
System hears: 0724

Prompt: "Enter time HHMM"
You press: 1, 8, 0, 0
System hears: 1800

Prompt: "Enter party size"
You press: 4
System hears: 4
```

**Variables Collected**:
- `VXML_RESULT_res_date=0724`
- `VXML_RESULT_res_time=1800`
- `VXML_RESULT_party_size=4`

These variables can be used in Asterisk dialplan for further processing (save to database, etc.)

---

### Test 5: Dynamic Selector VXML (Extension 504)

**Dial**: 504  
**Expected Behavior**:
1. Call connects
2. Greeting message: "Welcome"
3. System asks you to choose (1, 2, 3, 4)
4. Press your choice:
   - **1** → Calls hello.vxml
   - **2** → Calls menu-example.vxml
   - **3** → Calls transfer-example.vxml
   - **4** → Calls restaurant-booking-001.vxml
   - **Other** → "Invalid choice"

**Use Case**: Single entry point to multiple IVR scenarios

---

## 🐛 Troubleshooting

### Issue: Call connects but no audio

**Possible Causes**:
1. TTS provider not configured
2. Google Cloud credentials missing
3. Audio output device not working
4. VXML parsing error

**Solution**:
```bash
# Check Java logs for TTS errors
tail -f /var/log/java-ivr.log | grep -i tts

# Verify Google Cloud setup
echo $GOOGLE_APPLICATION_CREDENTIALS
# Should point to your service account JSON

# Test audio locally
# Try playing a static audio file via Asterisk:
asterisk -r -x "core show uptime"
# If Asterisk responds, audio device works
```

### Issue: DTMF not recognized

**Possible Causes**:
1. DTMF signal not sent from phone
2. Zoiper DTMF settings wrong
3. VoIP connection quality low

**Solution**:
```bash
# In Zoiper settings:
# Settings → Audio → DTMF: RFC 2833 or SIP INFO
# Try both options

# In Asterisk CLI, enable DTMF logging:
asterisk -r -x "logger level core debug"
# Try pressing keys and look for "DTMF_RECEIVED"
```

### Issue: Call hangs or times out

**Possible Causes**:
1. VXML file syntax error
2. Session timeout exceeded
3. JVoiceXML runtime crash

**Solution**:
```bash
# Check VXML file validity
mvn test -Dtest=VxmlValidatorTest

# Verify timeout settings
grep "vxml.session.timeout" src/main/resources/vxml-config.properties

# Check Java process is still running
ps aux | grep java | grep -v grep

# Check for Java exceptions in logs
tail -200 /var/log/asterisk/messages | grep -i exception
```

### Issue: Fast AGI connection refused

**Error Message**: `AGI connection refused 127.0.0.1:4573`

**Causes**:
1. Java FastAGI server not running
2. Port 4573 already in use
3. Firewall blocking

**Solution**:
```bash
# Start FastAGI server
cd IVR-engine
java -cp target/classes gov.iti.telecom.App &

# Or use Maven:
mvn exec:java -Dexec.mainClass="gov.iti.telecom.App"

# Check if 4573 is listening
ss -tlnp | grep 4573

# If still in use, find what's using it:
lsof -i :4573
# Kill if needed: kill -9 <PID>
```

---

## 📊 Monitoring & Debugging

### Enable Detailed Logging

**File**: `src/main/resources/logback.xml` (or use System.out)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="DEBUG">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

**Enable in Java**:
```bash
java -Dlogging.level.root=DEBUG -cp target/classes gov.iti.telecom.App &
```

### Monitor Asterisk in Real-time

```bash
# Real-time Asterisk logs
tail -f /var/log/asterisk/messages

# Or use Asterisk CLI with verbose output
asterisk -r
asterisk> logger level core verbose
asterisk> core show calls    # Show active calls
```

### Check Java FastAGI Server Logs

```bash
# If running in background
jobs -l

# Redirect logs to file for analysis
java -cp target/classes gov.iti.telecom.App > ivr-debug.log 2>&1 &

# Monitor in real-time
tail -f ivr-debug.log | grep -i vxml
```

---

## 🎓 Advanced Testing

### Test 1: Concurrent Calls

**Goal**: Verify thread-safety with multiple calls

**Steps**:
1. Open two Zoiper instances (1001 and 1002)
2. From 1001: Dial 500 (start hello)
3. Immediately from 1002: Dial 501 (start menu)
4. Both calls should work independently

**Expected**: No cross-contamination of session data

**Debug**: Check logs for session IDs:
```
[VxmlAgiHandler] Session ID: 1001_1719239400_1234
[VxmlAgiHandler] Session ID: 1002_1719239401_5678
# Should be different
```

---

### Test 2: Long-Running Calls

**Goal**: Verify timeout handling

**Steps**:
1. Dial 503 (restaurant booking)
2. When asked for input, wait 15+ seconds before responding
3. System should either:
   - Repeat prompt after timeout
   - Disconnect gracefully

**Expected**: No hanging connections

---

### Test 3: Error Handling

**Goal**: Test error paths

**Steps**:
1. Rename hello.vxml temporarily to hello_backup.vxml
2. Dial 500
3. System should play error message

**Expected**: Graceful error, not system crash

---

## 📋 Testing Checklist

### Before Testing
- [ ] Asterisk running
- [ ] FastAGI server running on port 4573
- [ ] VXML files in `scenarios/` directory
- [ ] Zoiper installed and logged in
- [ ] Google Cloud TTS credentials configured (if using TTS)

### First Call (Extension 500)
- [ ] Phone rings
- [ ] Audio plays (Hello message or TTS)
- [ ] Call ends normally
- [ ] No error in logs

### Menu Test (Extension 501)
- [ ] Menu prompt plays
- [ ] DTMF input accepted
- [ ] Correct branch taken based on input
- [ ] Variables set in Asterisk

### Complex Scenarios (502-503)
- [ ] Transfers work
- [ ] Form fields collected
- [ ] Session completes normally

---

## 🚀 Production Readiness Checklist

Before deploying to production:

- [ ] All VXML files validated (VxmlValidator passing)
- [ ] Timeout settings tuned for your SLA
- [ ] TTS provider tested under load
- [ ] Error handling tested for common failure cases
- [ ] Concurrent call testing (10+ simultaneous calls)
- [ ] Logging configured for troubleshooting
- [ ] Backup audio files for TTS fallback
- [ ] Security: FastAGI port behind firewall
- [ ] Performance: Session management under load

---

## 📞 Quick Reference

### Common Commands

```bash
# Check Asterisk status
systemctl status asterisk

# Check FastAGI server
ps aux | grep java
ss -tlnp | grep 4573

# View Asterisk logs
tail -f /var/log/asterisk/messages

# View Asterisk config
cat /etc/asterisk/extensions.conf | grep -A 5 "exten => 500"

# Reload Asterisk dialplan
asterisk -r -x "dialplan reload"

# Restart FastAGI
pkill -f "java.*App"
java -cp target/classes gov.iti.telecom.App &
```

### VXML Files Reference

| Extension | VXML File | Type | Purpose |
|-----------|-----------|------|---------|
| 500 | hello.vxml | Simple | Basic greeting |
| 501 | menu-example.vxml | Interactive | Menu with DTMF |
| 502 | transfer-example.vxml | Transfer | Chain VXML files |
| 503 | restaurant-booking-001.xml | Complex Form | Multi-field collection |

---

## ✅ Success Indicators

You'll know everything is working when:

1. ✅ Dial 500 → Hear "Hello. Welcome to the IVR system."
2. ✅ Dial 501 → Hear menu, press 1, hear "You selected reservations"
3. ✅ Dial 502 → Hear transfer message, then menu prompt
4. ✅ Dial 503 → Can enter date, time, party size
5. ✅ No errors in logs
6. ✅ Session variables populated correctly

---

## 📞 Support

**Issue**: Still not working?

1. Check logs: `tail -f /var/log/asterisk/messages`
2. Verify FastAGI: `ss -tlnp | grep 4573`
3. Test DTMF: `asterisk -r` → `logger level core debug` → try dialing
4. Check VXML: `mvn test -Dtest=VxmlValidatorTest`

---

**Document Version**: 1.0  
**Last Updated**: 2026-07-24
