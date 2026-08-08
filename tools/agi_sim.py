#!/usr/bin/env python3
"""AGI simulator: talks the FastAGI protocol to a running FastAgiServerMain.

Usage:
  python3 agi_sim.py --port 4573 --digits 135 --scenario default \
        [--ai-wav /path/to/speech.wav] [--out /tmp/opencode/sim/x.log]
"""
import argparse
import os
import shutil
import socket
import sys
import time

HEADERS = [
    "agi_network: yes",
    "agi_network_script: {script}",
    "agi_request: agi://127.0.0.1:{port}/{script}",
    "agi_channel: SIP/1001-00000001",
    "agi_language: en",
    "agi_type: SIP",
    "agi_uniqueid: 1234567890.1",
    "agi_version: 22.10.1",
    "agi_callerid: 1001",
    "agi_calleridname: E2E Tester",
    "agi_callingpres: 0",
    "agi_callingani2: 0",
    "agi_callington: 0",
    "agi_callingtns: 0",
    "agi_dnid: 500",
    "agi_rdnis: unknown",
    "agi_context: e2e",
    "agi_extension: 500",
    "agi_priority: 1",
    "agi_enhanced: 0.0",
    "agi_accountcode:",
    "agi_threadid: 1",
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=4573)
    ap.add_argument("--script", default="default")
    ap.add_argument("--vxml", default="e2e-test")
    ap.add_argument("--digits", default="")
    ap.add_argument("--barge", default=None,
                    help="'N:D' = return DTMF digit D during the Nth DTMF-aware STREAM FILE "
                         "(default N=1). Simulates a mid-prompt keypress.")
    ap.add_argument("--ai-wav", default=None)
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    digits = list(args.digits)
    ai_wav = args.ai_wav
    transcript = []
    max_ai_loops = 6
    ai_loop_count = 0
    barge_at = None
    barge_digit = None
    if args.barge:
        parts = args.barge.split(":")
        barge_digit = parts[-1]
        barge_at = int(parts[0]) if len(parts) == 2 else 1
    stream_count = 0

    sock = socket.create_connection(("127.0.0.1", args.port), timeout=120)
    sock.settimeout(120)
    f = sock.makefile("rwb", buffering=0)

    def send(line):
        transcript.append(">>> " + line)
        f.write((line + "\n").encode())

    # Handshake: send headers then blank line.
    for h in HEADERS:
        f.write((h.format(port=args.port, script=args.script) + "\n").encode())
    f.write(b"\n")

    t0 = time.time()
    try:
        while True:
            line = f.readline()
            if not line:
                transcript.append("<<< EOF (server closed connection)")
                break
            cmd = line.decode().rstrip("\r\n")
            if not cmd:
                continue
            transcript.append("<<< " + cmd)

            upper = cmd.upper()
            if upper.startswith("GET VARIABLE "):
                var = cmd[13:].strip().strip('"')
                if var == "DIALSTATUS":
                    send("200 result=1 (ANSWER)")
                elif var == "VXML_FILE":
                    send("200 result=1 (%s)" % args.vxml)
                else:
                    send("200 result=0")
            elif upper.startswith("STREAM FILE "):
                if barge_digit and '"0123456789*#"'.lower() in cmd.lower():
                    stream_count += 1
                    if stream_count == barge_at:
                        transcript.append("   [BARGE DTMF: %s]" % barge_digit)
                        send("200 result=%d" % ord(barge_digit))
                        continue
                send("200 result=0 endpos=2000")
            elif upper.startswith("WAIT FOR DIGIT "):
                if digits:
                    ch = digits.pop(0)
                    transcript.append("   [DTMF: %s]" % ch)
                    send("200 result=%d" % ord(ch))
                else:
                    send("200 result=0")
            elif upper.startswith("RECORD FILE "):
                # RECORD FILE "/dev/shm/..." "wav" "#" 5000 0 s=2000
                parts = cmd.split()
                if len(parts) >= 2:
                    path = parts[2].strip('"')
                    if ai_wav and os.path.exists(ai_wav) and path.startswith("/dev/shm/ai_"):
                        target = path + ".wav"
                        shutil.copy(ai_wav, target)
                        transcript.append("   [ASR wav created: %s]" % target)
                        ai_loop_count += 1
                        if ai_loop_count >= max_ai_loops:
                            transcript.append("   [MAX AI LOOPS REACHED]")
                            send("200 result=0")
                            break
                send("200 result=0")
            elif upper.startswith("EXEC "):
                send("200 result=0")
            elif upper.startswith("SET VARIABLE "):
                send("200 result=0")
            elif upper.startswith("HANGUP"):
                send("200 result=1")
                transcript.append("<<< EOF (hangup)")
                break
            elif upper.startswith("VERIFY"):
                send("200 result=0")
            elif upper.startswith("CHANNEL STATUS"):
                send("200 result=6")
            else:
                send("200 result=0")
    except socket.timeout:
        transcript.append("<<< TIMEOUT after %.1fs" % (time.time() - t0))
    except Exception as e:
        transcript.append("<<< ERROR: %r" % e)
    finally:
        try:
            sock.close()
        except Exception:
            pass

    out = "\n".join(transcript)
    print(out)
    if args.out:
        os.makedirs(os.path.dirname(args.out), exist_ok=True)
        with open(args.out, "w") as fh:
            fh.write(out + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
