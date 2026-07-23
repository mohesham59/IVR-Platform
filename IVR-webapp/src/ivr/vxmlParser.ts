import type { FlowNode, FlowEdge, NodeType } from './types'
import { NODE_DEFS } from './nodeConfig'

/**
 * vxmlParser — Parses standard VoiceXML 2.1 XML text into canvas nodes and edges.
 */
export function parseVxml(vxmlContent: string): { nodes: FlowNode[]; edges: FlowEdge[] } {
  const parser = new DOMParser()
  const xmlDoc = parser.parseFromString(vxmlContent, 'text/xml')

  const parserError = xmlDoc.getElementsByTagName('parsererror')
  if (parserError.length > 0) {
    throw new Error('Invalid VoiceXML document format: ' + parserError[0].textContent)
  }

  const root = xmlDoc.documentElement
  if (root.tagName.toLowerCase() !== 'vxml') {
    throw new Error('Root element must be <vxml>')
  }

  const nodes: FlowNode[] = []
  const edges: FlowEdge[] = []

  const forms = Array.from(xmlDoc.getElementsByTagName('form'))
  const menus = Array.from(xmlDoc.getElementsByTagName('menu'))

  let x = 100
  let y = 100
  const xGap = 260
  const yGap = 160

  let edgeCounter = 1

  // Process forms
  forms.forEach((form, idx) => {
    const id = form.getAttribute('id') || `n_form_${idx + 1}`
    const prompts = form.getElementsByTagName('prompt')
    const audioTags = form.getElementsByTagName('audio')
    const fields = form.getElementsByTagName('field')
    const transfers = form.getElementsByTagName('transfer')
    const gotos = form.getElementsByTagName('goto')
    const disconnects = form.getElementsByTagName('disconnect')

    let type: NodeType = 'playback'
    let title = `Form ${id}`
    let subtitle = 'VoiceXML Form Dialog'
    let audioFile: string | undefined = undefined
    let varName: string | undefined = undefined
    let transferDest: string | undefined = undefined

    if (fields.length > 0) {
      type = 'dtmf_input'
      varName = fields[0].getAttribute('name') || undefined
      subtitle = `Collect Field ${varName || id}`
    } else if (transfers.length > 0) {
      type = 'transfer'
      transferDest = transfers[0].getAttribute('dest') || 'SIP/100'
      subtitle = `Transfer to ${transferDest}`
    } else if (disconnects.length > 0) {
      type = 'end'
      subtitle = 'End Call / Disconnect'
    } else if (audioTags.length > 0) {
      type = 'playback'
      audioFile = audioTags[0].getAttribute('src') || undefined
      subtitle = audioFile ? `Play ${audioFile}` : (prompts[0]?.textContent || 'Audio playback')
    } else if (prompts.length > 0) {
      type = 'tts'
      subtitle = prompts[0].textContent?.trim() || 'Text-to-speech prompt'
    }

    const def = NODE_DEFS[type] || NODE_DEFS['playback']

    nodes.push({
      id,
      type,
      x: x + (idx % 3) * xGap,
      y: y + Math.floor(idx / 3) * yGap,
      title,
      subtitle,
      status: 'valid',
      collapsed: false,
      disabled: false,
      ports: def.outputPorts.map(p => ({ ...p, type: 'output' })),
      vxmlFormId: id,
      vxmlPrompt: prompts[0]?.textContent || undefined,
      vxmlAudioFile: audioFile,
      vxmlVarName: varName,
      vxmlDest: transferDest,
    })

    // Process outgoing goto
    Array.from(gotos).forEach(g => {
      const nextAttr = g.getAttribute('next')
      if (nextAttr) {
        const targetId = nextAttr.replace(/^#/, '')
        edges.push({
          id: `e${edgeCounter++}`,
          sourceId: id,
          sourcePort: 'out',
          targetId,
          targetPort: 'in',
          label: 'next',
          animated: true,
        })
      }
    })
  })

  // Process menus
  menus.forEach((menu, idx) => {
    const id = menu.getAttribute('id') || `n_menu_${idx + 1}`
    const prompts = menu.getElementsByTagName('prompt')
    const choices = Array.from(menu.getElementsByTagName('choice'))

    const def = NODE_DEFS['dtmf_menu']

    nodes.push({
      id,
      type: 'dtmf_menu',
      x: x + ((forms.length + idx) % 3) * xGap,
      y: y + Math.floor((forms.length + idx) / 3) * yGap,
      title: `Menu ${id}`,
      subtitle: prompts[0]?.textContent?.trim() || 'DTMF Selection Menu',
      status: 'valid',
      collapsed: false,
      disabled: false,
      ports: def.outputPorts.map(p => ({ ...p, type: 'output' })),
      vxmlFormId: id,
      vxmlPrompt: prompts[0]?.textContent || undefined,
    })

    choices.forEach(c => {
      const dtmf = c.getAttribute('dtmf') || '1'
      const nextAttr = c.getAttribute('next')
      if (nextAttr) {
        const targetId = nextAttr.replace(/^#/, '')
        edges.push({
          id: `e${edgeCounter++}`,
          sourceId: id,
          sourcePort: `opt_${dtmf}`,
          targetId,
          targetPort: 'in',
          label: dtmf,
          animated: true,
        })
      }
    })
  })

  return { nodes, edges }
}
