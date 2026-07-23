import { useState, useEffect } from 'react'
import { X, Copy, Download, Check, Code, FileText, Play } from 'lucide-react'
import { generateVxml } from '../ivr/vxmlGenerator'
import type { FlowNode, FlowEdge } from '../ivr/types'

interface VxmlModalProps {
  isOpen: boolean
  onClose: () => void
  nodes: FlowNode[]
  edges: FlowEdge[]
  onImportVxml?: (vxmlCode: string) => void
}

export default function VxmlModal({ isOpen, onClose, nodes, edges, onImportVxml }: VxmlModalProps) {
  const [vxmlCode, setVxmlCode] = useState('')
  const [copied, setCopied] = useState(false)
  const [activeTab, setActiveTab] = useState<'view' | 'edit'>('view')
  const [editableCode, setEditableCode] = useState('')

  useEffect(() => {
    if (isOpen) {
      const generated = generateVxml(nodes, edges, 'Hospital_IVR_Flow')
      setVxmlCode(generated)
      setEditableCode(generated)
    }
  }, [isOpen, nodes, edges])

  if (!isOpen) return null

  const handleCopy = () => {
    navigator.clipboard.writeText(vxmlCode)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleDownload = () => {
    const blob = new Blob([vxmlCode], { type: 'application/voicexml+xml;charset=utf-8;' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'ivr-scenario.vxml'
    link.click()
  }

  const handleApplyEdit = () => {
    if (onImportVxml) {
      onImportVxml(editableCode)
      onClose()
    }
  }

  return (
    <div className="fixed inset-0 z-[999] bg-black/50 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl w-full max-w-4xl max-h-[85vh] flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#E5E7EB] bg-[#F8FAFC]">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#2563EB] flex items-center justify-center text-white">
              <Code className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-[#1F2937] font-bold text-base">VoiceXML 2.1 Standard View</h3>
              <p className="text-[#6B7280] text-xs">W3C VoiceXML Standard Document Interchange</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="bg-[#F3F4F6] p-1 rounded-lg flex gap-1">
              <button
                onClick={() => setActiveTab('view')}
                className={`px-3 py-1 text-xs font-semibold rounded-md transition-colors ${activeTab === 'view' ? 'bg-white text-[#2563EB] shadow-sm' : 'text-[#6B7280]'}`}
              >
                VXML Output
              </button>
              <button
                onClick={() => setActiveTab('edit')}
                className={`px-3 py-1 text-xs font-semibold rounded-md transition-colors ${activeTab === 'edit' ? 'bg-white text-[#2563EB] shadow-sm' : 'text-[#6B7280]'}`}
              >
                VXML Editor
              </button>
            </div>

            <button onClick={onClose} className="p-2 rounded-lg text-[#9CA3AF] hover:bg-[#E5E7EB] hover:text-[#1F2937] transition-colors">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-hidden p-6 bg-[#0F172A] text-[#E2E8F0] font-mono text-xs">
          {activeTab === 'view' ? (
            <textarea
              readOnly
              value={vxmlCode}
              className="w-full h-full bg-transparent text-[#E2E8F0] outline-none resize-none"
            />
          ) : (
            <textarea
              value={editableCode}
              onChange={e => setEditableCode(e.target.value)}
              className="w-full h-full bg-transparent text-[#38BDF8] outline-none resize-none focus:ring-1 focus:ring-[#38BDF8] p-2 rounded-lg border border-[#334155]"
              placeholder="Paste or write VoiceXML 2.1 code here..."
            />
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-4 border-t border-[#E5E7EB] bg-[#F8FAFC]">
          <div className="flex items-center gap-2 text-xs text-[#6B7280]">
            <FileText className="w-4 h-4 text-[#2563EB]" />
            <span>VoiceXML 2.1 W3C Standard Compliant</span>
          </div>

          <div className="flex items-center gap-3">
            {activeTab === 'edit' ? (
              <button
                onClick={handleApplyEdit}
                className="flex items-center gap-2 px-4 py-2 bg-[#2563EB] hover:bg-[#1E40AF] text-white text-xs font-semibold rounded-xl transition-all shadow-md"
              >
                <Play className="w-4 h-4" />
                Apply VXML to Canvas
              </button>
            ) : (
              <>
                <button
                  onClick={handleCopy}
                  className="flex items-center gap-2 px-4 py-2 bg-white border border-[#E5E7EB] hover:bg-[#F3F4F6] text-[#374151] text-xs font-semibold rounded-xl transition-colors"
                >
                  {copied ? <Check className="w-4 h-4 text-[#22C55E]" /> : <Copy className="w-4 h-4" />}
                  {copied ? 'Copied VXML!' : 'Copy VXML'}
                </button>
                <button
                  onClick={handleDownload}
                  className="flex items-center gap-2 px-4 py-2 bg-[#2563EB] hover:bg-[#1E40AF] text-white text-xs font-semibold rounded-xl transition-all shadow-md"
                >
                  <Download className="w-4 h-4" />
                  Export .vxml File
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
