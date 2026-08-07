#!/bin/bash
set -e

# Resolve absolute path to add_extension.sh in the workspace
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ADD_EXT_PATH="${SCRIPT_DIR}/IVR-engine/add_extension.sh"

if [ ! -f "$ADD_EXT_PATH" ]; then
    echo "Error: add_extension.sh not found at ${ADD_EXT_PATH}"
    exit 1
fi

echo "Adding passwordless sudo permission for ${ADD_EXT_PATH}..."
SUDOERS_FILE="/etc/sudoers.d/nexus_ivr"
SUDOERS_LINE="${USER} ALL=(ALL) NOPASSWD: /bin/bash ${ADD_EXT_PATH} *"

echo "${SUDOERS_LINE}" | sudo tee "${SUDOERS_FILE}" > /dev/null
sudo chmod 0440 "${SUDOERS_FILE}"

echo "Configuring Asterisk configuration and control permissions..."
sudo sed -i 's/^;astctlpermissions = 0660/astctlpermissions = 0660/' /etc/asterisk/asterisk.conf 2>/dev/null || true
sudo sed -i 's/^;astctlowner = root/astctlowner = asterisk/' /etc/asterisk/asterisk.conf 2>/dev/null || true
sudo sed -i 's/^;astctlgroup = apache/astctlgroup = asterisk/' /etc/asterisk/asterisk.conf 2>/dev/null || true
sudo chmod g+w /etc/asterisk/extensions.conf 2>/dev/null || true
sudo systemctl restart asterisk 2>/dev/null || true

echo "Setup completed successfully!"
