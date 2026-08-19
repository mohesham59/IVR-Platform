import { useState, useEffect } from 'react'
import TenantLayout from '../components/TenantLayout'
import { backendUrl, fetchWithRetry } from '../api/backendUrl'
import {
  CreditCard, CheckCircle2, AlertCircle, Calendar, ArrowRight, RefreshCw, Zap, X
} from 'lucide-react'

interface SubscriptionPlan {
  id: string
  name: string
  pricePiasters: number
  billingInterval: string
  integrationIds?: string
}

interface Transaction {
  id: string
  tenantId: string
  type: string
  amountPiasters: number
  currency: string
  status: string
  paymobTransactionId?: string
  paymobOrderId?: string
  planId?: string
  createdAt?: string
}

interface TenantInfo {
  id: string
  displayName: string
  subscriptionStatus: string
  subscriptionPlanId?: string
  subscriptionExpiresAt?: string
}

export default function TenantBilling({ onLogout }: { onLogout: () => void }) {
  const [tenant, setTenant] = useState<TenantInfo | null>(null)
  const [plans, setPlans] = useState<SubscriptionPlan[]>([])
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Plan Selection / Checkout state
  const [isInitiating, setIsInitiating] = useState(false)
  const [showPlanSelector, setShowPlanSelector] = useState(false)

  // Cancel state: tracks which txn is being cancelled
  const [cancellingId, setCancellingId] = useState<string | null>(null)

  const fetchData = async () => {
    setLoading(true)
    setError(null)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {}
      if (token) headers['Authorization'] = `Bearer ${token}`

      // Decode activeTenantId directly from the JWT payload (base64, no library needed)
      let jwtActiveTenantId: string | null = null
      if (token) {
        try {
          const payloadB64 = token.split('.')[1]
          const payload = JSON.parse(atob(payloadB64.replace(/-/g, '+').replace(/_/g, '/')))
          jwtActiveTenantId = payload.activeTenantId || null
        } catch {
          // ignore decode errors
        }
      }

      // 1. Fetch Tenant Companies / Active Tenant Info
      let tenantRes = await fetchWithRetry('/api/v1/tenant/companies', { headers }).catch(() => null)
      if (!tenantRes || !tenantRes.ok) {
        tenantRes = await fetchWithRetry(backendUrl('/api/v1/tenant/companies'), { headers }).catch(() => null)
      }

      // Build activeTenant: prefer companies API result, fall back to JWT-decoded ID
      let activeTenant: TenantInfo = {
        id: jwtActiveTenantId || '',
        displayName: 'My Workspace',
        subscriptionStatus: 'ACTIVE'
      }

      if (tenantRes && tenantRes.ok) {
        const data = await tenantRes.json()
        if (data.success && Array.isArray(data.tenants)) {
          const found = data.tenants.find((t: any) => t.isActive) || data.tenants[0]
          if (found) {
            activeTenant = {
              id: found.id,
              displayName: found.displayName,
              subscriptionStatus: found.subscriptionStatus || 'ACTIVE',
              subscriptionPlanId: found.subscriptionPlanId,
              subscriptionExpiresAt: found.subscriptionExpiresAt
            }
          }
        }
        // Also expose the activeTenantId returned by the companies endpoint
        if (data.activeTenantId && !activeTenant.id) {
          activeTenant = { ...activeTenant, id: data.activeTenantId }
        }
      }
      setTenant(activeTenant)

      // 2. Fetch Subscription Plans
      const plansRes = await fetchWithRetry(backendUrl('/api/payments/plans'), { headers })
      if (plansRes && plansRes.ok) {
        const plansData = await plansRes.json()
        const planList = Array.isArray(plansData) ? plansData : []
        setPlans(planList)
      }

      // 3. Fetch Tenant Payment History
      // Always send the X-Tenant-ID header so verifyTenantAuth can pick it up even
      // when the JWT does not have activeTenantId (e.g. user was assigned a tenant after login).
      const historyHeaders: Record<string, string> = { ...headers }
      if (activeTenant.id) historyHeaders['X-Tenant-ID'] = activeTenant.id
      const historyUrl = activeTenant.id
        ? backendUrl(`/api/payments/history?tenantId=${activeTenant.id}`)
        : backendUrl('/api/payments/history')
      const historyRes = await fetchWithRetry(historyUrl, { headers: historyHeaders })
      if (historyRes && historyRes.ok) {
        const historyData = await historyRes.json()
        setTransactions(Array.isArray(historyData) ? historyData : [])
      }

    } catch (err: any) {
      console.error('Error fetching billing info:', err)
      setError(err.message || 'Could not load billing information')
    } finally {
      setLoading(false)
    }
  }


  useEffect(() => {
    fetchData()
  }, [])

  const handleInitiateCheckout = async (planIdToBuy: string) => {
    if (!tenant) return
    setIsInitiating(true)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Tenant-ID': tenant.id
      }
      if (token) headers['Authorization'] = `Bearer ${token}`

      const payload = {
        tenantId: tenant.id,
        planId: planIdToBuy,
        redirectionUrl: window.location.origin + '/payment/callback'
      }

      const res = await fetch('/api/payments/subscription/initiate', {
        method: 'POST',
        headers,
        body: JSON.stringify(payload)
      })

      if (!res.ok) {
        throw new Error(`Failed to initiate checkout: HTTP ${res.status}`)
      }

      const data = await res.json()
      if (data.checkoutUrl) {
        if (data.transactionId) {
          sessionStorage.setItem('nexus_pending_transaction_id', data.transactionId)
        }
        // Redirect browser to Paymob Unified Checkout hosted page
        window.location.href = data.checkoutUrl
      } else {
        throw new Error('Response did not contain a checkout URL')
      }
    } catch (err: any) {
      alert(err.message || 'Error redirecting to Paymob checkout')
      setIsInitiating(false)
    }
  }

  const handleCancelTransaction = async (txnId: string) => {
    if (!tenant || cancellingId) return
    if (!window.confirm('Cancel this pending transaction? This cannot be undone.')) return
    setCancellingId(txnId)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = { 'Content-Type': 'application/json' }
      if (token) headers['Authorization'] = `Bearer ${token}`

      const res = await fetch('/api/payments/cancel', {
        method: 'POST',
        headers,
        body: JSON.stringify({ transactionId: txnId })
      })

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}))
        throw new Error(errData.error || `HTTP ${res.status}`)
      }

      // Optimistically update the UI immediately, then refetch for accuracy
      setTransactions(prev =>
        prev.map(t => t.id === txnId ? { ...t, status: 'CANCELLED' } : t)
      )
      // Background refresh for full accuracy
      fetchData()
    } catch (err: any) {
      alert('Failed to cancel transaction: ' + (err.message || 'Unknown error'))
    } finally {
      setCancellingId(null)
    }
  }

  const currentPlan = plans.find(p => p.id === tenant?.subscriptionPlanId) || plans[0]

  return (
    <TenantLayout
      pageTitle="Billing & Subscription Management"
      pageSubtitle="Manage your subscription plan, execute instant EGP renewals via Paymob, and download invoice receipts."
      onLogout={onLogout}
      headerActions={
        <button
          onClick={fetchData}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-[#E5E7EB] bg-white text-[#4B5563] text-xs font-semibold hover:bg-slate-50 transition-all shadow-sm"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      }
    >
      {/* Error Banner */}
      {error && (
        <div className="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 flex items-center justify-between text-sm shadow-sm">
          <div className="flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0" />
            <div>
              <p className="font-semibold">Unable to load billing data</p>
              <p className="text-xs text-red-600 mt-0.5">{error}</p>
            </div>
          </div>
          <button onClick={fetchData} className="px-3 py-1 bg-red-100 hover:bg-red-200 text-red-800 rounded-lg text-xs font-medium transition-all">
            Retry
          </button>
        </div>
      )}

      {/* Current Plan Overview Card */}
      <div className="bg-gradient-to-br from-slate-900 to-slate-800 text-white rounded-2xl p-6 shadow-xl mb-8 border border-slate-700 relative overflow-hidden">
        <div className="absolute right-0 top-0 translate-x-4 -translate-y-4 w-64 h-64 bg-blue-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-6 relative z-10">
          <div>
            <div className="flex items-center gap-3">
              <span className="px-3 py-1 rounded-full text-xs font-extrabold bg-[#2563EB] text-white tracking-wide uppercase shadow-sm">
                Current Subscription
              </span>
              {tenant?.subscriptionStatus === 'ACTIVE' && (
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 flex items-center gap-1">
                  <CheckCircle2 className="w-3.5 h-3.5" /> Active
                </span>
              )}
              {tenant?.subscriptionStatus === 'EXPIRED' && (
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-red-500/20 text-red-400 border border-red-500/30 flex items-center gap-1">
                  <AlertCircle className="w-3.5 h-3.5" /> Expired
                </span>
              )}
              {tenant?.subscriptionStatus === 'INACTIVE' && (
                <span className="px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-slate-500/20 text-slate-400 border border-slate-500/30 flex items-center gap-1">
                  <AlertCircle className="w-3.5 h-3.5" /> Inactive
                </span>
              )}
            </div>

            <h2 className="text-2xl font-black mt-3 text-white tracking-tight">
              {currentPlan ? currentPlan.name : 'No Active Plan'}
            </h2>

            <div className="mt-2 flex items-baseline gap-2">
              <span className="text-3xl font-extrabold text-white">
                {currentPlan ? (currentPlan.pricePiasters / 100).toLocaleString('en-US', { minimumFractionDigits: 2 }) : '—'}
              </span>
              {currentPlan && (
                <span className="text-sm text-slate-300 font-medium">EGP / {currentPlan.billingInterval.toLowerCase()}</span>
              )}
            </div>

            <p className="text-xs text-slate-400 mt-3 flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5 text-blue-400" />
              Renews / Expires on: <span className="font-semibold text-slate-200">{tenant?.subscriptionExpiresAt ? new Date(tenant.subscriptionExpiresAt).toLocaleDateString() : '30 days from purchase'}</span>
            </p>
          </div>

          {/* Action buttons — always show both when plans exist */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 w-full md:w-auto">
            <button
              onClick={() => setShowPlanSelector(!showPlanSelector)}
              className="px-5 py-3 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-bold transition-all border border-white/10 flex items-center justify-center gap-2"
            >
              <Zap className="w-4 h-4 text-amber-400" />
              {showPlanSelector ? 'Hide Plans' : 'Change Plan'}
            </button>
            {currentPlan && (
              <button
                onClick={() => currentPlan && handleInitiateCheckout(currentPlan.id)}
                disabled={isInitiating}
                className="px-6 py-3 rounded-xl bg-[#2563EB] hover:bg-[#1D4ED8] text-white text-xs font-bold transition-all shadow-lg flex items-center justify-center gap-2 disabled:opacity-50"
              >
                <CreditCard className="w-4 h-4" />
                {isInitiating ? 'Redirecting...' : 'Renew Now'}
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Plan Selector — shown when toggled */}
      {showPlanSelector && (
        <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm p-6 mb-8 animate-in fade-in slide-in-from-top-4 duration-200">
          <div className="flex items-center justify-between mb-5">
            <div>
              <h3 className="text-base font-bold text-[#1F2937]">Select a Subscription Plan</h3>
              <p className="text-xs text-[#6B7280] mt-0.5">Choose a plan below and click Pay to start a Paymob checkout.</p>
            </div>
            <button
              onClick={() => setShowPlanSelector(false)}
              className="p-1.5 rounded-lg hover:bg-slate-100 text-[#6B7280] hover:text-[#1F2937] transition-all"
            >
              <X className="w-4 h-4" />
            </button>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {plans.map((p) => {
              const isCurrent = p.id === currentPlan?.id
              return (
                <div
                  key={p.id}
                  className={`rounded-xl border p-5 transition-all relative ${
                    isCurrent
                      ? 'border-[#2563EB] bg-[#EFF6FF]/40 ring-2 ring-[#2563EB]/20'
                      : 'border-[#E5E7EB] hover:border-slate-300 hover:shadow-sm'
                  }`}
                >
                  {isCurrent && (
                    <span className="absolute top-3 right-3 px-2 py-0.5 rounded-full bg-[#2563EB] text-white text-[10px] font-bold tracking-wide">
                      CURRENT
                    </span>
                  )}
                  <h4 className="font-bold text-slate-800 text-base pr-14">{p.name}</h4>
                  <p className="text-2xl font-black text-slate-900 mt-2">
                    {(p.pricePiasters / 100).toLocaleString()} <span className="text-xs font-normal text-slate-500">EGP / {p.billingInterval.toLowerCase()}</span>
                  </p>
                  <button
                    onClick={() => !isCurrent && handleInitiateCheckout(p.id)}
                    disabled={isInitiating || isCurrent}
                    className={`mt-4 w-full py-2 px-4 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-1.5 ${
                      isCurrent
                        ? 'bg-slate-100 text-slate-400 cursor-not-allowed'
                        : 'bg-[#2563EB] text-white hover:bg-[#1D4ED8] disabled:opacity-50'
                    }`}
                  >
                    {isCurrent ? 'Current Plan' : (<>Pay & Upgrade <ArrowRight className="w-3.5 h-3.5" /></>)}
                  </button>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Invoice & Payment History Table */}
      <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-[#E5E7EB] flex items-center justify-between bg-slate-50/50">
          <div>
            <h3 className="text-base font-bold text-[#1F2937]">Payment & Invoice History</h3>
            <p className="text-xs text-[#6B7280] mt-0.5">Records of all one-time and recurring EGP checkout transactions</p>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-[#374151]">
            <thead className="bg-[#F8FAFC] border-b border-[#E5E7EB] text-[#6B7280] font-semibold uppercase tracking-wider">
              <tr>
                <th className="px-6 py-3">Date</th>
                <th className="px-6 py-3">Transaction ID</th>
                <th className="px-6 py-3">Type</th>
                <th className="px-6 py-3">Amount (EGP)</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3">Paymob Reference</th>
                <th className="px-6 py-3">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E5E7EB]">
              {transactions.map((txn) => (
                <tr key={txn.id} className="hover:bg-slate-50/80 transition-colors">
                  <td className="px-6 py-3.5 text-[#6B7280] font-medium whitespace-nowrap">
                    {txn.createdAt ? new Date(txn.createdAt).toLocaleString() : 'Recent'}
                  </td>
                  <td className="px-6 py-3.5 font-mono text-[11px] text-slate-800 max-w-[120px] truncate" title={txn.id}>
                    {txn.id.slice(0, 8)}…
                  </td>
                  <td className="px-6 py-3.5 font-semibold text-slate-700">
                    {txn.type}
                  </td>
                  <td className="px-6 py-3.5 font-bold text-slate-900">
                    {(txn.amountPiasters / 100).toLocaleString('en-US', { minimumFractionDigits: 2 })} EGP
                  </td>
                  <td className="px-6 py-3.5">
                    {txn.status === 'SUCCESS' && (
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#F0FDF4] text-[#16A34A] border border-[#DCFCE7] inline-flex items-center gap-1">
                        <CheckCircle2 className="w-3 h-3" /> Paid
                      </span>
                    )}
                    {txn.status === 'PENDING' && (
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#FEFCE8] text-[#CA8A04] border border-[#FEF08A] inline-flex items-center gap-1">
                        <Calendar className="w-3 h-3" /> Pending
                      </span>
                    )}
                    {txn.status === 'FAILED' && (
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#FEF2F2] text-[#DC2626] border border-[#FEE2E2] inline-flex items-center gap-1">
                        <AlertCircle className="w-3 h-3" /> Failed
                      </span>
                    )}
                    {txn.status === 'EXPIRED' && (
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#FFF7ED] text-[#C2410C] border border-[#FFEDD5] inline-flex items-center gap-1">
                        <AlertCircle className="w-3 h-3" /> Expired
                      </span>
                    )}
                    {txn.status === 'CANCELLED' && (
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-bold bg-[#F3F4F6] text-[#6B7280] border border-[#E5E7EB] inline-flex items-center gap-1">
                        <X className="w-3 h-3" /> Cancelled
                      </span>
                    )}
                  </td>
                  <td className="px-6 py-3.5 font-mono text-[11px] text-slate-500">
                    {txn.paymobOrderId ? `Order #${txn.paymobOrderId.slice(0, 12)}…` : '—'}
                  </td>
                  <td className="px-6 py-3.5">
                    {txn.status === 'PENDING' ? (
                      <button
                        id={`cancel-txn-${txn.id}`}
                        onClick={() => handleCancelTransaction(txn.id)}
                        disabled={cancellingId === txn.id}
                        className="px-2.5 py-1 rounded-md text-[10px] font-bold text-red-600 border border-red-200 bg-red-50 hover:bg-red-100 transition-all disabled:opacity-50 flex items-center gap-1 whitespace-nowrap"
                      >
                        <X className="w-3 h-3" />
                        {cancellingId === txn.id ? 'Cancelling…' : 'Cancel'}
                      </button>
                    ) : (
                      <span className="text-slate-300 text-[11px]">—</span>
                    )}
                  </td>
                </tr>
              ))}

              {transactions.length === 0 && !loading && (
                <tr>
                  <td colSpan={7} className="px-6 py-8 text-center text-[#9CA3AF]">
                    No payment invoices found for this tenant account.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </TenantLayout>
  )
}
