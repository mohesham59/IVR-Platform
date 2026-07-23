import type { FlowNode, FlowEdge } from './types'

/**
 * vxmlGenerator — Converts canvas nodes & edges into W3C VoiceXML 2.1 standard document.
 */
export function generateVxml(nodes: FlowNode[], edges: FlowEdge[], scenarioName: string = 'IVR_Flow'): string {
  const outgoingMap = new Map<string, Array<{ targetId: string; label?: string }>>()
  edges.forEach(e => {
    const list = outgoingMap.get(e.sourceId) || []
    list.push({ targetId: e.targetId, label: e.label })
    outgoingMap.set(e.sourceId, list)
  })

  let xml = `<?xml version="1.0" encoding="UTF-8"?>\n`
  xml += `<vxml version="2.1" xmlns="http://www.w3.org/2001/vxml" xml:lang="en-US">\n`
  xml += `  <!-- VoiceXML 2.1 Standard Flow: ${escapeXml(scenarioName)} -->\n`
  xml += `  <meta name="generator" content="NexusIVR VXML Studio"/>\n`
  xml += `  <property name="inputmodes" value="dtmf tone"/>\n`
  xml += `  <property name="timeout" value="5s"/>\n\n`

  const startNode = nodes.find(n => n.type === 'start') || nodes[0]
  if (startNode) {
    xml += `  <var name="caller_id" expr="session.connection.remote.uri"/>\n`
    xml += `  <var name="current_step" expr="'${startNode.id}'"/>\n\n`
  }

  nodes.forEach(node => {
    const nextEdges = outgoingMap.get(node.id) || []
    const defaultNextId = nextEdges[0]?.targetId || 'end'

    switch (node.type) {
      case 'start':
        xml += `  <!-- Start Node -->\n`
        xml += `  <form id="${node.id}">\n`
        xml += `    <block>\n`
        xml += `      <log expr="'IVR session started for ' + caller_id"/>\n`
        xml += `      <goto next="#${defaultNextId}"/>\n`
        xml += `    </block>\n`
        xml += `  </form>\n\n`
        break

      case 'greeting':
      case 'playback':
      case 'tts':
        xml += `  <!-- Prompt Dialog -->\n`
        xml += `  <form id="${node.id}">\n`
        xml += `    <block>\n`
        if (node.vxmlAudioFile) {
          xml += `      <audio src="${escapeXml(node.vxmlAudioFile)}">${escapeXml(node.subtitle || node.title)}</audio>\n`
        } else {
          xml += `      <prompt>${escapeXml(node.subtitle || node.title)}</prompt>\n`
        }
        xml += `      <goto next="#${defaultNextId}"/>\n`
        xml += `    </block>\n`
        xml += `  </form>\n\n`
        break

      case 'dtmf_menu':
        xml += `  <!-- Menu Dialog -->\n`
        xml += `  <menu id="${node.id}">\n`
        xml += `    <prompt>\n`
        xml += `      ${escapeXml(node.subtitle || node.title || 'Main Menu')}\n`
        xml += `    </prompt>\n`
        nextEdges.forEach((edge, idx) => {
          const dtmfDigit = edge.label || (idx + 1).toString()
          xml += `    <choice dtmf="${escapeXml(dtmfDigit)}" next="#${edge.targetId}">Choice ${dtmfDigit}</choice>\n`
        })
        xml += `    <noinput>\n`
        xml += `      <prompt>We did not receive any key. Please try again.</prompt>\n`
        xml += `      <reprompt/>\n`
        xml += `    </noinput>\n`
        xml += `    <nomatch>\n`
        xml += `      <prompt>That selection is invalid. Please try again.</prompt>\n`
        xml += `      <reprompt/>\n`
        xml += `    </nomatch>\n`
        xml += `  </menu>\n\n`
        break

      case 'dtmf_input':
      case 'record':
        const varName = node.vxmlVarName || `input_${node.id}`
        xml += `  <!-- Input/Field Dialog -->\n`
        xml += `  <form id="${node.id}">\n`
        xml += `    <field name="${varName}">\n`
        xml += `      <prompt>${escapeXml(node.subtitle || 'Please enter digits')}</prompt>\n`
        xml += `      <grammar mode="dtmf" type="application/srgs+xml">\n`
        xml += `        <rule id="digits" scope="public"><one-of><item>0</item><item>1</item><item>2</item><item>3</item><item>4</item><item>5</item><item>6</item><item>7</item><item>8</item><item>9</item></one-of></rule>\n`
        xml += `      </grammar>\n`
        xml += `      <filled>\n`
        xml += `        <log expr="'Collected input: ' + ${varName}"/>\n`
        xml += `        <goto next="#${defaultNextId}"/>\n`
        xml += `      </filled>\n`
        xml += `    </field>\n`
        xml += `  </form>\n\n`
        break

      case 'transfer':
      case 'extension':
      case 'queue':
        const dest = node.vxmlDest || 'SIP/100'
        xml += `  <!-- Transfer Dialog -->\n`
        xml += `  <form id="${node.id}">\n`
        xml += `    <transfer name="call_transfer" dest="${escapeXml(dest)}" bridge="true">\n`
        xml += `      <prompt>Connecting your call, please hold.</prompt>\n`
        xml += `      <filled>\n`
        xml += `        <goto next="#${defaultNextId}"/>\n`
        xml += `      </filled>\n`
        xml += `    </transfer>\n`
        xml += `  </form>\n\n`
        break

      case 'end':
      case 'voicemail':
        xml += `  <!-- End/Disconnect Dialog -->\n`
        xml += `  <form id="${node.id}">\n`
        xml += `    <block>\n`
        xml += `      <prompt>Thank you for calling. Goodbye.</prompt>\n`
        xml += `      <disconnect/>\n`
        xml += `    </block>\n`
        xml += `  </form>\n\n`
        break

      default:
        xml += `  <!-- Generic Form -->\n`
        xml += `  <form id="${node.id}">\n`
        xml += `    <block>\n`
        xml += `      <prompt>${escapeXml(node.title)}</prompt>\n`
        xml += `      <goto next="#${defaultNextId}"/>\n`
        xml += `    </block>\n`
        xml += `  </form>\n\n`
        break
    }
  })

  xml += `</vxml>\n`
  return xml
}

function escapeXml(unsafe: string): string {
  return unsafe
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;')
}
