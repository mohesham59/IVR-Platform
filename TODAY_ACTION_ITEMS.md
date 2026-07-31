# 🚀 TODAY'S ACTION ITEMS - Do This Now!

**Time**: 2026-07-24  
**Goal**: Get your VXML engine working with Zoiper in 15 minutes  
**Status**: All code created, just need to deploy and test

---

## ✅ What's Done (Don't Do This)

- ✅ VxmlLoader, VxmlValidator, VxmlConfig, VxmlSession created
- ✅ VxmlAgiHandler created (smart FastAGI handler)
- ✅ fastagi-mapping.properties updated
- ✅ extensions.conf.new created with 5 test extensions
- ✅ VXML test files ready (hello.vxml, menu-example.vxml, etc.)
- ✅ Complete documentation written

---

## 📋 DO THIS RIGHT NOW (15 min)

### Step 1: Update Asterisk Config (2 min)

```bash
# Go to Asterisk config directory
cd /etc/asterisk

# Backup current config
cp extensions.conf extensions.conf.backup

# Option A: Replace entire file (if you're starting fresh)
cp /path/to/extensions.conf.new extensions.conf

# Option B: Manual merge (if you have other extensions)
# Manually add these extensions to your extensions.conf:
# [From: /path/to/extensions.conf.new]
# Look for:  exten => 500,1...  through  exten => 504,1...
# Copy those lines into your extensions.conf under [default] context
```

### Step 2: Reload Asterisk (1 min)

```bash
# Reload the dialplan
asterisk -r -x "dialplan reload"

# Verify it loaded
asterisk -r -x "dialplan show default" | grep "500"
# Should show: exten => 500,1,NoOp(=== VXML IVR: Hello World ===)
```

### Step 3: Compile Java Code (2 min)

```bash
cd /home/omar/windows/D/omar/Telecom_ITI/500_GarduationProject/Ivr_project/IVR-Platform/IVR-engine

mvn clean compile

# Should end with: [INFO] BUILD SUCCESS
```

### Step 4: Start FastAGI Server (2 min)

```bash
# In a new terminal (keep it open for monitoring)
java -cp target/classes gov.iti.telecom.App &

# You should see output like:
# [VxmlLoader] Initialized with resource path: scenarios/
# [VxmlAgiHandler] VXML selected: hello
# [FastAGI] Server ready on port 4573

# Verify it's listening
ss -tlnp | grep 4573
# Should show: LISTEN ... :4573
```

### Step 5: Test with Zoiper (8 min)

#### If Zoiper Already Configured:

1. Open Zoiper
2. Make sure you're logged in as extension 1001 (password: 1234)
3. Dial: **500**
4. **Listen** → You should hear: "Hello. Welcome to the IVR system."
5. Call ends

✅ **If you heard the message → SUCCESS!** Your VXML engine is working!

#### If Zoiper Not Configured Yet:

1. Install Zoiper (from zoiper.com or app store)
2. Open it → Create New Account
3. Settings:
   - **SIP Server**: 192.168.x.x (your Asterisk server IP)
   - **Username**: 1001
   - **Password**: 1234
4. Click Save/Register
5. Wait for "Registered" status
6. Dial 500
7. Listen for audio

---

## ❌ If It Doesn't Work

### Issue: "Call Failed" or "Connection Refused"

**Check FastAGI Server**:
```bash
# Is Java running?
ps aux | grep java | grep -v grep

# Is port 4573 open?
ss -tlnp | grep 4573

# If not, start it:
java -cp target/classes gov.iti.telecom.App &
```

### Issue: "Call Connects but No Audio"

**Check TTS Provider**:
```bash
# Make sure Google Cloud credentials are set
echo $GOOGLE_APPLICATION_CREDENTIALS

# If not set:
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/your/credentials.json

# Restart Java:
pkill -f "java.*App"
java -cp target/classes gov.iti.telecom.App &
```

### Issue: "Asterisk Can't Find Extension 500"

**Reload Dialplan**:
```bash
asterisk -r -x "dialplan reload"

# Then try:
asterisk -r -x "dialplan show default" | grep "500"
# Should show your extension
```

---

## 📞 Test Each Extension (After Step 5 Works)

### Extension 500 (Simple)
```
Dial: 500
Expected: Audio "Hello. Welcome to IVR system"
Time: <2 seconds
Result: ✅ Call ends
```

### Extension 501 (Interactive Menu)
```
Dial: 501
Wait: ~3 seconds for prompt
Press: 1
Expected: Audio "You selected reservations"
Result: ✅ Call ends
```

### Extension 502 (Transfer)
```
Dial: 502
Expected: Audio "Transferring you to the next menu"
Then: Menu prompt plays
Result: ✅ Works with chain
```

### Extension 503 (Complex Form)
```
Dial: 503
Wait for: "Enter date MMDD"
Press: 7, 2, 4 (for July 24)
Wait for: "Enter time HHMM"
Press: 1, 8, 0, 0 (for 18:00)
Result: ✅ Form fields collected
```

### Extension 504 (Dynamic Selector)
```
Dial: 504
Wait for: Greeting + selection prompt
Press: 1 (for hello VXML)
Expected: "Hello. Welcome to IVR system"
Result: ✅ Flexible routing works
```

---

## 📊 Success Checklist

After completing all steps above, check off:

- [ ] Asterisk reloaded dialplan
- [ ] Java FastAGI server running on port 4573
- [ ] Zoiper logged in as 1001
- [ ] Dialed 500 and heard audio
- [ ] No errors in `/var/log/asterisk/messages`
- [ ] No errors in Java console/logs
- [ ] Session variables visible in Asterisk

---

## 🎯 After "Hello" Works (Extension 500)

Once you successfully hear the "Hello" message, you have:

✅ **End-to-End Flow Working**:
- Phone → Asterisk → FastAGI → VxmlAgiHandler → VxmlLoader → JVoiceXML → TTS → Audio → Phone

✅ **All Components Integrated**:
- Asterisk dialplan ✓
- FastAGI server ✓
- VXML loading ✓
- Session management ✓
- Audio output ✓

---

## 📚 Documentation Reference

| Document | Use When | Time |
|----------|----------|------|
| THIS FILE | Getting started RIGHT NOW | 15 min |
| VXML_TESTING_GUIDE.md | Need detailed test procedures | 20 min |
| ASTERISK_INTEGRATION_SUMMARY.md | Need architecture overview | 10 min |
| IVR_IMPLEMENTATION_PLAN.md | Need complete roadmap | 30 min |
| QUICK_START.md | Need code examples | 15 min |

---

## 💡 Pro Tips

### Tip 1: Monitor in Real-Time
```bash
# Terminal 1: Watch Asterisk logs
tail -f /var/log/asterisk/messages | grep -i "agi\|vxml"

# Terminal 2: Watch Java logs
# If running foreground, you'll see logs automatically
```

### Tip 2: Quick Debug
```bash
# Is Asterisk accepting calls?
asterisk -r -x "core show calls"

# Can FastAGI reach Java?
telnet 127.0.0.1 4573
# Should connect; press Ctrl+C to exit

# Is VXML file valid?
mvn test -Dtest=VxmlValidatorTest
```

### Tip 3: Reset Everything
```bash
# If things get stuck:
pkill -f "java.*App"          # Kill Java
asterisk -r -x "core restart"  # Restart Asterisk
sleep 5
java -cp target/classes gov.iti.telecom.App &  # Start Java again
```

---

## 🎉 When You're Done

Once extension 500 works:

1. Test other extensions (501-504) to understand each feature
2. Read **ASTERISK_INTEGRATION_SUMMARY.md** to understand architecture
3. Read **VXML_TESTING_GUIDE.md** for advanced testing
4. Next week: Implement VxmlScenarioEngine (Stage 2)

---

## ⏱️ Time Breakdown

| Task | Time |
|------|------|
| Update Asterisk | 2 min |
| Compile | 2 min |
| Start Java | 1 min |
| Test with Zoiper | 8 min |
| Debug (if needed) | 5-10 min |
| **Total** | **~20 minutes** |

---

## 📞 Help!

If you get stuck:

1. **Check logs first**:
   - `tail -f /var/log/asterisk/messages`
   - Java console output

2. **Verify services**:
   - `ss -tlnp | grep 5060` (Asterisk)
   - `ss -tlnp | grep 4573` (FastAGI)

3. **Common solutions**:
   - Port already in use? → Kill process and restart
   - No audio? → Check TTS credentials
   - Call hangs? → Check timeout in config
   - Extension not found? → Reload dialplan

---

## Next Steps (After This Works)

- [ ] Test all 5 extensions (500-504)
- [ ] Read ASTERISK_INTEGRATION_SUMMARY.md
- [ ] Document any issues found
- [ ] Week 2: Start implementing VxmlScenarioEngine

---

**GO! Start with Step 1 above! You've got this! 🚀**

---

**Created**: 2026-07-24  
**Status**: Ready to deploy and test  
**Estimated Success**: 15-20 minutes
