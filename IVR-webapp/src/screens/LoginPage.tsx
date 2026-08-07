import { useState } from 'react'
import { backendUrl } from '../api/backendUrl'
import { Eye, EyeOff, Phone, Cpu, Cloud, Zap, Shield, Globe, CheckCircle } from 'lucide-react'

interface Props {
  onLogin: (email: string) => void
}

export default function LoginPage({ onLogin }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)
  const [isLoading, setIsLoading] = useState(false)

  const [errorMsg, setErrorMsg] = useState('')

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)
    setErrorMsg('')
    try {
      let res = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      }).catch(() => null)

      if (!res) {
        res = await fetch(backendUrl('/api/v1/auth/login'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password })
        }).catch(() => null)
      }

      if (!res) {
        setIsLoading(false)
        setErrorMsg('Unable to connect to authentication server.')
        return
      }

      const data = await res.json()
      setIsLoading(false)
      if (res.ok && data.success) {
        if (data.token) {
          localStorage.setItem('nexus_jwt_token', data.token)
          localStorage.setItem('nexus_user', JSON.stringify(data.user))
        }
        onLogin(data.user?.isSuperadmin ? 'admin@nexusivr.com' : email)
      } else {
        setErrorMsg(data.message || 'Invalid email or password.')
      }
    } catch (err: any) {
      setIsLoading(false)
      setErrorMsg('Unable to connect to authentication server.')
    }
  }

  return (
    <div className="min-h-screen flex" style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Left panel — illustration */}
      <div
        className="hidden lg:flex flex-col justify-between w-[52%] relative overflow-hidden"
        style={{ background: 'linear-gradient(145deg, #1E40AF 0%, #2563EB 45%, #3B82F6 100%)' }}
      >
        {/* Decorative rings */}
        <div className="absolute -top-32 -left-32 w-[520px] h-[520px] rounded-full border border-white/10" />
        <div className="absolute -top-20 -left-20 w-[380px] h-[380px] rounded-full border border-white/10" />
        <div className="absolute -bottom-40 -right-40 w-[600px] h-[600px] rounded-full border border-white/8" />
        <div className="absolute bottom-20 right-20 w-[340px] h-[340px] rounded-full border border-white/10" />

        {/* Grid dots */}
        <div
          className="absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage: 'radial-gradient(circle, #fff 1px, transparent 1px)',
            backgroundSize: '32px 32px',
          }}
        />

        {/* Logo top */}
        <div className="relative z-10 p-10">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-white/20 flex items-center justify-center backdrop-blur-sm">
              <Phone className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="text-white font-bold text-lg leading-tight">NexusIVR</div>
              <div className="text-white/60 text-xs">AI Contact Center Platform</div>
            </div>
          </div>
        </div>

        {/* Center illustration */}
        <div className="relative z-10 flex-1 flex flex-col items-center justify-center px-12 -mt-8">
          {/* Central hub */}
          <div className="relative flex items-center justify-center w-64 h-64">
            {/* Pulsing rings */}
            <div className="absolute w-64 h-64 rounded-full border border-white/20 animate-ping" style={{ animationDuration: '3s' }} />
            <div className="absolute w-48 h-48 rounded-full border border-white/25" />
            <div className="absolute w-32 h-32 rounded-full bg-white/10 backdrop-blur-sm border border-white/20" />

            {/* Core */}
            <div className="w-20 h-20 rounded-2xl bg-white/20 backdrop-blur-md border border-white/30 flex items-center justify-center shadow-2xl">
              <Cpu className="w-10 h-10 text-white" />
            </div>

            {/* Orbiting icons */}
            {[
              { icon: <Cloud className="w-5 h-5 text-white" />, top: '-8px', left: '50%', transform: 'translateX(-50%)' },
              { icon: <Zap className="w-5 h-5 text-white" />, top: '50%', right: '-8px', transform: 'translateY(-50%)' },
              { icon: <Shield className="w-5 h-5 text-white" />, bottom: '-8px', left: '50%', transform: 'translateX(-50%)' },
              { icon: <Globe className="w-5 h-5 text-white" />, top: '50%', left: '-8px', transform: 'translateY(-50%)' },
            ].map((item, i) => (
              <div
                key={i}
                className="absolute w-10 h-10 rounded-xl bg-white/20 backdrop-blur-sm border border-white/30 flex items-center justify-center"
                style={{ top: item.top, left: item.left, right: item.right, bottom: item.bottom, transform: item.transform }}
              >
                {item.icon}
              </div>
            ))}

            {/* Connecting lines */}
            <svg className="absolute inset-0 w-full h-full" viewBox="0 0 256 256" fill="none">
              <line x1="128" y1="0" x2="128" y2="96" stroke="white" strokeOpacity="0.2" strokeDasharray="4 4" />
              <line x1="256" y1="128" x2="160" y2="128" stroke="white" strokeOpacity="0.2" strokeDasharray="4 4" />
              <line x1="128" y1="256" x2="128" y2="160" stroke="white" strokeOpacity="0.2" strokeDasharray="4 4" />
              <line x1="0" y1="128" x2="96" y2="128" stroke="white" strokeOpacity="0.2" strokeDasharray="4 4" />
            </svg>
          </div>

          <h2 className="text-white text-3xl font-bold text-center mt-10 leading-snug">
            Intelligent IVR.<br />Powered by AI.
          </h2>
          <p className="text-white/70 text-center mt-4 max-w-xs text-sm leading-relaxed">
            Build, deploy, and monitor enterprise-grade contact center flows — no code required.
          </p>

          {/* Feature chips */}
          <div className="flex flex-wrap gap-2 justify-center mt-8">
            {['Multi-Tenant', 'AI Voice Bot', 'Real-Time Analytics', 'SIP Integration', 'Zero-Code Builder'].map((f) => (
              <div key={f} className="flex items-center gap-1.5 bg-white/10 border border-white/20 rounded-full px-3 py-1">
                <CheckCircle className="w-3 h-3 text-white/80" />
                <span className="text-white/90 text-xs font-medium">{f}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom stats */}
        <div className="relative z-10 p-10 grid grid-cols-3 gap-6 border-t border-white/10">
          {[
            { val: '3,400+', label: 'Companies' },
            { val: '12M+', label: 'Calls / Month' },
            { val: '99.99%', label: 'Uptime SLA' },
          ].map((s) => (
            <div key={s.label}>
              <div className="text-white font-bold text-xl">{s.val}</div>
              <div className="text-white/55 text-xs mt-0.5">{s.label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Right panel — form */}
      <div className="flex-1 flex flex-col">
        {/* Mobile logo */}
        <div className="lg:hidden p-6 flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-[#2563EB] flex items-center justify-center">
            <Phone className="w-4 h-4 text-white" />
          </div>
          <div className="font-bold text-[#1F2937]">NexusIVR</div>
        </div>

        <div className="flex-1 flex items-center justify-center p-8">
          <div className="w-full max-w-[400px]">
            {/* Header */}
            <div className="mb-8">
              <div className="inline-flex items-center gap-2 bg-[#EFF6FF] border border-[#BFDBFE] rounded-full px-3 py-1 mb-5">
                <div className="w-1.5 h-1.5 rounded-full bg-[#2563EB]" />
                <span className="text-[#2563EB] text-xs font-semibold">Secure Portal</span>
              </div>
              <h1 className="text-[#1F2937] text-[28px] font-bold leading-tight">Welcome back</h1>
              <p className="text-[#6B7280] text-sm mt-2">Sign in to your NexusIVR account to continue.</p>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-5">
              {errorMsg && (
                <div className="p-3.5 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs font-medium">
                  {errorMsg}
                </div>
              )}
              <div>
                <label className="block text-[#374151] text-sm font-medium mb-1.5">Email address</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@nexusivr.com"
                  required
                  className="w-full h-11 px-4 rounded-xl border border-[#E5E7EB] bg-white text-[#1F2937] text-sm placeholder-[#9CA3AF] outline-none transition-all focus:border-[#2563EB] focus:ring-4 focus:ring-[#2563EB]/10"
                />
              </div>

              <div>
                <label className="block text-[#374151] text-sm font-medium mb-1.5">Password</label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••••"
                    required
                    className="w-full h-11 px-4 pr-11 rounded-xl border border-[#E5E7EB] bg-white text-[#1F2937] text-sm placeholder-[#9CA3AF] outline-none transition-all focus:border-[#2563EB] focus:ring-4 focus:ring-[#2563EB]/10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[#9CA3AF] hover:text-[#6B7280] transition-colors"
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <label className="flex items-center gap-2 cursor-pointer">
                  <div
                    onClick={() => setRememberMe(!rememberMe)}
                    className={`w-4 h-4 rounded border-2 flex items-center justify-center transition-all cursor-pointer ${
                      rememberMe ? 'bg-[#2563EB] border-[#2563EB]' : 'border-[#D1D5DB]'
                    }`}
                  >
                    {rememberMe && (
                      <svg className="w-2.5 h-2.5 text-white" viewBox="0 0 10 10" fill="none">
                        <path d="M1.5 5L4 7.5L8.5 2.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    )}
                  </div>
                  <span className="text-[#374151] text-sm">Remember me</span>
                </label>
                <a href="#" className="text-[#2563EB] text-sm font-medium hover:text-[#1E40AF] transition-colors">
                  Forgot password?
                </a>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="w-full h-11 rounded-xl bg-[#2563EB] hover:bg-[#1E40AF] text-white text-sm font-semibold transition-all shadow-lg shadow-[#2563EB]/25 disabled:opacity-70 flex items-center justify-center gap-2"
              >
                {isLoading ? (
                  <>
                    <svg className="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="3" />
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                    </svg>
                    Signing in…
                  </>
                ) : (
                  'Sign in'
                )}
              </button>
            </form>

            {/* Hint */}
            <div className="mt-6 p-4 rounded-xl bg-[#F8FAFC] border border-[#E5E7EB]">
              <p className="text-[#6B7280] text-xs">
                <span className="font-semibold text-[#374151]">Demo Credentials:</span><br />
                • Super Admin: <code className="bg-[#E5E7EB] px-1 rounded text-[#1F2937]">admin@nexusivr.com</code> / <code className="bg-[#E5E7EB] px-1 rounded text-[#1F2937]">admin</code><br />
                • Tenant Admin: <code className="bg-[#E5E7EB] px-1 rounded text-[#1F2937]">user@nexusivr.com</code> / <code className="bg-[#E5E7EB] px-1 rounded text-[#1F2937]">user</code>
              </p>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="p-6 border-t border-[#F3F4F6] flex items-center justify-between">
          <p className="text-[#9CA3AF] text-xs">© 2025 NexusIVR Inc. All rights reserved.</p>
          <div className="flex gap-4">
            {['Privacy', 'Terms', 'Support'].map((l) => (
              <a key={l} href="#" className="text-[#9CA3AF] text-xs hover:text-[#6B7280] transition-colors">
                {l}
              </a>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
