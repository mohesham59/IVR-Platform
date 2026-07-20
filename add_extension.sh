#!/bin/bash

# Configuration file path
CONF_FILE="/etc/asterisk/extensions.conf"

# Check if correct number of arguments are provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <extension> <business_name>"
    echo "Example: $0 1000 'Pizza Place'"
    exit 1
fi

EXTENSION=$1
BUSINESS_NAME=$2

# Check if the config file is writable
if [ ! -w "$CONF_FILE" ]; then
  echo "Error: Cannot write to $CONF_FILE. Please ensure the current user has write permission."
  echo "Run: sudo chmod o+w $CONF_FILE"
  exit 1
fi

# Append the extension and business name to the file
# The dialplan instructions can be modified as needed for your specific IVR logic
echo "" >> "$CONF_FILE"
echo "; Business: $BUSINESS_NAME" >> "$CONF_FILE"
echo "exten => $EXTENSION,1,NoOp(Incoming call for $BUSINESS_NAME)" >> "$CONF_FILE"
# Assuming you want to trigger the FastAGI server for the IVR platform
echo "exten => $EXTENSION,n,AGI(agi://127.0.0.1:4573/ivr_platform?business_name=$BUSINESS_NAME)" >> "$CONF_FILE"
echo "exten => $EXTENSION,n,Hangup()" >> "$CONF_FILE"

echo "Extension $EXTENSION for '$BUSINESS_NAME' added successfully to $CONF_FILE."
