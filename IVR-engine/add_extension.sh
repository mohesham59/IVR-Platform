#!/bin/bash

# Configuration file path
CONF_FILE="/etc/asterisk/extensions.conf"

# Check if correct number of arguments are provided
if [ "$#" -lt 2 ] || [ "$#" -gt 4 ]; then
    echo "Usage: $0 <extension> <vxml_scenario_name> [vxml_file_path] [tenant_id]"
    echo "Example: $0 500 'restaurant-booking-001'"
    exit 1
fi

EXTENSION=$1
SCENARIO_NAME=$2
VXML_FILE_PATH=${3:-$SCENARIO_NAME}
TENANT_ID=$4

# Check if the config file is writable
if [ ! -w "$CONF_FILE" ]; then
  echo "Error: Cannot write to $CONF_FILE. Please ensure the current user has write permission."
  echo "Run: sudo chmod o+w $CONF_FILE"
  exit 1
fi

# Remove any existing dialplan entries for this extension to guarantee idempotency
sed -i "/^exten => ${EXTENSION},/d" "$CONF_FILE" 2>/dev/null || true
sed -i "/^; VXML Scenario: .* (ext ${EXTENSION})/d" "$CONF_FILE" 2>/dev/null || true

# Format TENANT_ID assignment if provided
TENANT_SET_LINE=""
if [ -n "$TENANT_ID" ]; then
    TENANT_SET_LINE="    print \"exten => '"$EXTENSION"',n,Set(TENANT_ID='"$TENANT_ID"')\""
fi

# Insert the extension before [menu] if present, otherwise append to end of file
if grep -q "^\[menu\]" "$CONF_FILE"; then
  awk '/^\[menu\]/{
    print "; VXML Scenario: '"$SCENARIO_NAME"' (ext '"$EXTENSION"')"
    print "exten => '"$EXTENSION"',1,NoOp(Incoming call for VXML Scenario: '"$SCENARIO_NAME"')"
    print "exten => '"$EXTENSION"',n,Answer()"
    print "exten => '"$EXTENSION"',n,Set(VXML_FILE='"$VXML_FILE_PATH"')"
'"$TENANT_SET_LINE"'
    print "exten => '"$EXTENSION"',n,AGI(agi://127.0.0.1:4573/default)"
    print "exten => '"$EXTENSION"',n,Hangup()"
    print ""
  }1' "$CONF_FILE" > /tmp/asterisk_ext_tmp && cat /tmp/asterisk_ext_tmp > "$CONF_FILE" && rm -f /tmp/asterisk_ext_tmp
else
  TENANT_APPEND=""
  if [ -n "$TENANT_ID" ]; then
      TENANT_APPEND="exten => $EXTENSION,n,Set(TENANT_ID=$TENANT_ID)"
  fi
  cat << EOF >> "$CONF_FILE"

; VXML Scenario: $SCENARIO_NAME (ext $EXTENSION)
exten => $EXTENSION,1,NoOp(Incoming call for VXML Scenario: $SCENARIO_NAME)
exten => $EXTENSION,n,Answer()
exten => $EXTENSION,n,Set(VXML_FILE=$VXML_FILE_PATH)
$TENANT_APPEND
exten => $EXTENSION,n,AGI(agi://127.0.0.1:4573/default)
exten => $EXTENSION,n,Hangup()
EOF
fi

echo "Extension $EXTENSION for scenario '$SCENARIO_NAME' added successfully to $CONF_FILE."

# Reload the dialplan so Asterisk picks up the new extension immediately
asterisk -rx "dialplan reload"
echo "Asterisk dialplan reloaded successfully."
