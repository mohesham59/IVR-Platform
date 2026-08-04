#!/bin/bash

# Configuration file path
CONF_FILE="${CONF_FILE:-/etc/asterisk/extensions.conf}"

# Check if correct number of arguments are provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <extension> <vxml_scenario_name>"
    echo "Example: $0 500 'restaurant-booking-001'"
    exit 1
fi

EXTENSION=$1
SCENARIO_NAME=$2

# Check if the config file is writable
if [ ! -w "$CONF_FILE" ]; then
  echo "Error: Cannot write to $CONF_FILE. Please ensure the current user has write permission."
  echo "Run: sudo chmod o+w $CONF_FILE"
  exit 1
fi

# Remove any existing dialplan entries for this extension to guarantee idempotency
sed -i "/^exten => ${EXTENSION},/d" "$CONF_FILE" 2>/dev/null || true
sed -i "/^; Business: .* (ext ${EXTENSION})/d" "$CONF_FILE" 2>/dev/null || true

# Append the extension and scenario name to the file
echo "" >> "$CONF_FILE"
echo "; Business: $SCENARIO_NAME (ext $EXTENSION)" >> "$CONF_FILE"
echo "exten => $EXTENSION,1,NoOp(Incoming call for $SCENARIO_NAME)" >> "$CONF_FILE"
echo "exten => $EXTENSION,n,AGI(agi://127.0.0.1:4573/ivr_platform?business_name=$SCENARIO_NAME)" >> "$CONF_FILE"
echo "exten => $EXTENSION,n,Hangup()" >> "$CONF_FILE"


echo "Extension $EXTENSION for scenario '$SCENARIO_NAME' added successfully to $CONF_FILE."

# Reload the dialplan so Asterisk picks up the new extension immediately
asterisk -rx "dialplan reload"
echo "Asterisk dialplan reloaded successfully."
