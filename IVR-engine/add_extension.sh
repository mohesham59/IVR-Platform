#!/bin/bash

# Configuration file path
CONF_FILE="/etc/asterisk/extensions.conf"

# Check if correct number of arguments are provided
if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    echo "Usage: $0 <extension> <vxml_scenario_name> [vxml_file_path]"
    echo "Example: $0 500 'restaurant-booking-001'"
    exit 1
fi

EXTENSION=$1
SCENARIO_NAME=$2
VXML_FILE_PATH=${3:-$SCENARIO_NAME}

# Check if the config file is writable
if [ ! -w "$CONF_FILE" ]; then
  echo "Error: Cannot write to $CONF_FILE. Please ensure the current user has write permission."
  echo "Run: sudo chmod o+w $CONF_FILE"
  exit 1
fi

# Remove any existing dialplan entries for this extension to guarantee idempotency
sed -i "/^exten => ${EXTENSION},/d" "$CONF_FILE" 2>/dev/null || true
sed -i "/^; VXML Scenario: .* (ext ${EXTENSION})/d" "$CONF_FILE" 2>/dev/null || true

# Insert the extension before the [menu] section so it goes into the [default] context
awk '/^\[menu\]/{
  print "; VXML Scenario: '"$SCENARIO_NAME"' (ext '"$EXTENSION"')"
  print "exten => '"$EXTENSION"',1,NoOp(Incoming call for VXML Scenario: '"$SCENARIO_NAME"')"
  print "exten => '"$EXTENSION"',n,Answer()"
  print "exten => '"$EXTENSION"',n,Set(VXML_FILE='"$VXML_FILE_PATH"')"
  print "exten => '"$EXTENSION"',n,AGI(agi://127.0.0.1:4573/default)"
  print "exten => '"$EXTENSION"',n,Hangup()"
  print ""
}1' "$CONF_FILE" > /tmp/asterisk_ext_tmp && cat /tmp/asterisk_ext_tmp > "$CONF_FILE" && rm -f /tmp/asterisk_ext_tmp

echo "Extension $EXTENSION for scenario '$SCENARIO_NAME' added successfully to $CONF_FILE."

# Reload the dialplan so Asterisk picks up the new extension immediately
asterisk -rx "dialplan reload"
echo "Asterisk dialplan reloaded successfully."
