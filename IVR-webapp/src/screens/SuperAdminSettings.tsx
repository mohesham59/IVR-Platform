import { useState } from 'react'
import SuperAdminLayout from '../components/SuperAdminLayout'
import { Save, Shield, Mail, Bell, Server, Settings as SettingsIcon } from 'lucide-react'

export default function SuperAdminSettings({ onLogout }: { onLogout: () => void }) {
  const [activeTab, setActiveTab] = useState('general')

  const tabs = [
    { id: 'general', label: 'General', icon: SettingsIcon },
    { id: 'security', label: 'Security', icon: Shield },
    { id: 'smtp', label: 'Email & SMTP', icon: Mail },
    { id: 'notifications', label: 'Alerts & Notifications', icon: Bell },
    { id: 'advanced', label: 'Advanced', icon: Server },
  ]

  const [isSaving, setIsSaving] = useState(false)

  const handleSave = () => {
    setIsSaving(true)
    setTimeout(() => setIsSaving(false), 800)
  }

  return (
    <SuperAdminLayout
      pageTitle="System Settings"
      pageSubtitle="Configure global platform settings, security, and integrations"
      onLogout={onLogout}
      headerActions={
        <button 
          onClick={handleSave}
          className="flex items-center gap-2 px-4 py-2 bg-[#2563EB] hover:bg-[#1E40AF] text-white text-sm font-medium rounded-lg transition-all shadow-sm"
        >
          <Save className="w-4 h-4" />
          {isSaving ? 'Saving...' : 'Save Changes'}
        </button>
      }
    >
      <div className="flex gap-6 h-full min-h-[500px]">
        {/* Settings Navigation */}
        <div className="w-64 flex-shrink-0">
          <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-3 flex flex-col gap-1">
            {tabs.map(tab => {
              const Icon = tab.icon
              const active = activeTab === tab.id
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${
                    active 
                      ? 'bg-[#EFF6FF] text-[#2563EB]' 
                      : 'text-[#4B5563] hover:bg-[#F9FAFB] hover:text-[#1F2937]'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  {tab.label}
                </button>
              )
            })}
          </div>
        </div>

        {/* Settings Content */}
        <div className="flex-1 max-w-4xl bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-6 overflow-y-auto">
          {activeTab === 'general' && (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div>
                <h3 className="text-lg font-semibold text-[#1F2937]">General Settings</h3>
                <p className="text-sm text-[#6B7280]">Manage basic platform details and global limits.</p>
              </div>
              
              <div className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium text-[#374151]">Platform Name</label>
                    <input type="text" defaultValue="Nexus IVR Platform" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium text-[#374151]">Support Email</label>
                    <input type="email" defaultValue="support@nexusivr.com" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium text-[#374151]">Global Max Call Duration (mins)</label>
                    <input type="number" defaultValue="120" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-sm font-medium text-[#374151]">Default Language</label>
                    <select className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]">
                      <option>English (US)</option>
                      <option>Arabic</option>
                      <option>French</option>
                    </select>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'security' && (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div>
                <h3 className="text-lg font-semibold text-[#1F2937]">Security Policies</h3>
                <p className="text-sm text-[#6B7280]">Enforce global authentication and security standards.</p>
              </div>
              
              <div className="space-y-4">
                <div className="flex items-center justify-between p-4 border border-[#E5E7EB] rounded-lg">
                  <div>
                    <div className="text-sm font-medium text-[#1F2937]">Require Two-Factor Authentication (2FA)</div>
                    <div className="text-xs text-[#6B7280] mt-0.5">Force all Tenant Admins to setup 2FA upon login.</div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" defaultChecked className="sr-only peer" />
                    <div className="w-11 h-6 bg-[#E5E7EB] peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-[#2563EB]/20 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#2563EB]"></div>
                  </label>
                </div>
                
                <div className="flex items-center justify-between p-4 border border-[#E5E7EB] rounded-lg">
                  <div>
                    <div className="text-sm font-medium text-[#1F2937]">Strict Password Policy</div>
                    <div className="text-xs text-[#6B7280] mt-0.5">Require minimum 12 chars, uppercase, numbers, and symbols.</div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" defaultChecked className="sr-only peer" />
                    <div className="w-11 h-6 bg-[#E5E7EB] peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-[#2563EB]/20 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#2563EB]"></div>
                  </label>
                </div>

                <div className="space-y-1.5 mt-4 max-w-sm">
                  <label className="text-sm font-medium text-[#374151]">Session Timeout (Minutes)</label>
                  <input type="number" defaultValue="30" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                </div>
              </div>
            </div>
          )}

          {activeTab === 'smtp' && (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div>
                <h3 className="text-lg font-semibold text-[#1F2937]">Email & SMTP Configuration</h3>
                <p className="text-sm text-[#6B7280]">Settings for outgoing platform emails (invites, alerts, reports).</p>
              </div>
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="space-y-1.5 md:col-span-2">
                  <label className="text-sm font-medium text-[#374151]">SMTP Host</label>
                  <input type="text" defaultValue="smtp.mailgun.org" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-sm font-medium text-[#374151]">SMTP Port</label>
                  <input type="text" defaultValue="587" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-sm font-medium text-[#374151]">Encryption</label>
                  <select className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]">
                    <option>TLS</option>
                    <option>SSL</option>
                    <option>None</option>
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-sm font-medium text-[#374151]">SMTP Username</label>
                  <input type="text" defaultValue="postmaster@nexusivr.com" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                </div>
                <div className="space-y-1.5">
                  <label className="text-sm font-medium text-[#374151]">SMTP Password</label>
                  <input type="password" defaultValue="****************" className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]/20 focus:border-[#2563EB] bg-[#F9FAFB]" />
                </div>
              </div>
              <div className="pt-2">
                <button className="px-4 py-2 border border-[#E5E7EB] rounded-lg text-sm font-medium text-[#374151] hover:bg-[#F9FAFB] transition-colors">
                  Test Connection
                </button>
              </div>
            </div>
          )}

          {activeTab === 'notifications' && (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div>
                <h3 className="text-lg font-semibold text-[#1F2937]">Alerts & Notifications</h3>
                <p className="text-sm text-[#6B7280]">Configure internal system alerts for Super Admins.</p>
              </div>
              
              <div className="space-y-4">
                <div className="flex items-center justify-between p-4 border border-[#E5E7EB] rounded-lg">
                  <div>
                    <div className="text-sm font-medium text-[#1F2937]">High Resource Usage Alerts</div>
                    <div className="text-xs text-[#6B7280] mt-0.5">Send email if CPU/Memory exceeds 85% for 5 mins.</div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" defaultChecked className="sr-only peer" />
                    <div className="w-11 h-6 bg-[#E5E7EB] peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-[#2563EB]/20 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#2563EB]"></div>
                  </label>
                </div>
                
                <div className="flex items-center justify-between p-4 border border-[#E5E7EB] rounded-lg">
                  <div>
                    <div className="text-sm font-medium text-[#1F2937]">New Tenant Registration Alerts</div>
                    <div className="text-xs text-[#6B7280] mt-0.5">Notify when a new tenant signs up or is created.</div>
                  </div>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input type="checkbox" className="sr-only peer" />
                    <div className="w-11 h-6 bg-[#E5E7EB] peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-[#2563EB]/20 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#2563EB]"></div>
                  </label>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'advanced' && (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div>
                <h3 className="text-lg font-semibold text-[#EF4444]">Advanced Actions</h3>
                <p className="text-sm text-[#6B7280]">Danger zone. Proceed with caution.</p>
              </div>
              
              <div className="space-y-4">
                <div className="p-4 border border-[#FCA5A5] bg-[#FEF2F2] rounded-lg flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold text-[#991B1B]">Clear System Cache</h4>
                    <p className="text-xs text-[#7F1D1D] mt-0.5">Clears redis cache, compiled VXML files, and temporary records.</p>
                  </div>
                  <button className="px-4 py-2 bg-white border border-[#FCA5A5] text-[#DC2626] text-sm font-medium rounded-lg hover:bg-[#FEF2F2] transition-colors">
                    Clear Cache
                  </button>
                </div>
                
                <div className="p-4 border border-[#FCA5A5] bg-[#FEF2F2] rounded-lg flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold text-[#991B1B]">Force Maintenance Mode</h4>
                    <p className="text-xs text-[#7F1D1D] mt-0.5">Disconnects all active calls and prevents new logins.</p>
                  </div>
                  <button className="px-4 py-2 bg-[#DC2626] hover:bg-[#B91C1C] text-white text-sm font-medium rounded-lg transition-colors">
                    Enable Maintenance
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </SuperAdminLayout>
  )
}
