import { useState, useEffect } from 'react'
import SuperAdminLayout from '../components/SuperAdminLayout'
import {
  TrendingUp, CheckCircle2, AlertCircle, RefreshCw, Plus, Edit2, Trash2,
  Search, Filter, CreditCard, DollarSign, Calendar, ShieldCheck, X
} from 'lucide-react'

interface PaymentSummary {
  totalRevenuePiastersThisMonth: number
  activeSubscriptions: number
  failedPaymentsCount: number
}

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
  tenantDisplayName?: string
  type: string
  amountPiasters: number
  currency: string
  status: string
  paymobTransactionId?: string
  paymobOrderId?: string
  planId?: string
  createdAt?: string
}

import { fetchWithRetry } from '../api/backendUrl'

export default function SuperAdminSubscriptions({ onLogout }: { onLogout: () => void }) {
  const [summary, setSummary] = useState<PaymentSummary | null>(null)
  const [plans, setPlans] = useState<SubscriptionPlan[]>([])
  const [transactions, setTransactions] = useState<Transaction[]>([])

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Filtering states
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [searchQuery, setSearchQuery] = useState<string>('')

  // Plan Modal state
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingPlan, setEditingPlan] = useState<SubscriptionPlan | null>(null)
  const [planForm, setPlanForm] = useState({
    name: '',
    priceEgp: 500,
    billingInterval: 'MONTHLY',
    integrationIds: '5834828'
  })
  const [isSubmitting, setIsSubmitting] = useState(false)

  const fetchData = async () => {
    setLoading(true)
    setError(null)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'X-Superadmin': 'true',
        'X-User-Role': 'SUPERADMIN'
      }
      if (token) headers['Authorization'] = `Bearer ${token}`

      // 1. Fetch Summary
      const summaryRes = await fetchWithRetry('/api/payments/summary', { headers })
      if (summaryRes.ok) {
        const summaryData = await summaryRes.json()
        setSummary(summaryData)
      } else {
        throw new Error(`Failed to load summary: HTTP ${summaryRes.status}`)
      }

      // 2. Fetch Plans
      const plansRes = await fetchWithRetry('/api/payments/plans', { headers })
      if (plansRes.ok) {
        const plansData = await plansRes.json()
        setPlans(Array.isArray(plansData) ? plansData : [])
      }

      // 3. Fetch global transaction history (superadmin sees all tenants)
      const txRes = await fetchWithRetry('/api/payments/history', { headers })
      if (txRes.ok) {
        const txData = await txRes.json()
        setTransactions(Array.isArray(txData) ? txData : [])
      } else {
        console.warn('Failed to load transactions:', txRes.status)
        setTransactions([])
      }
    } catch (err: any) {
      console.error('Error loading payment data:', err)
      setError(err.message || 'Could not connect to IVR Payment Service')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
  }, [])

  const handleOpenCreateModal = () => {
    setEditingPlan(null)
    setPlanForm({ name: '', priceEgp: 500, billingInterval: 'MONTHLY', integrationIds: '5834828' })
    setIsModalOpen(true)
  }

  const handleOpenEditModal = (plan: SubscriptionPlan) => {
    setEditingPlan(plan)
    setPlanForm({
      name: plan.name,
      priceEgp: plan.pricePiasters / 100,
      billingInterval: plan.billingInterval,
      integrationIds: plan.integrationIds || '5834828'
    })
    setIsModalOpen(true)
  }

  const handleSavePlan = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsSubmitting(true)
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Superadmin': 'true',
        'X-User-Role': 'SUPERADMIN'
      }
      if (token) headers['Authorization'] = `Bearer ${token}`

      const payload = {
        id: editingPlan ? editingPlan.id : undefined,
        name: planForm.name,
        pricePiasters: Math.round(planForm.priceEgp * 100),
        billingInterval: planForm.billingInterval,
        integrationIds: planForm.integrationIds
      }

      const method = editingPlan ? 'PUT' : 'POST'
      const res = await fetch('/api/payments/plans', {
        method,
        headers,
        body: JSON.stringify(payload)
      })

      if (!res.ok) {
        throw new Error(`Failed to save plan: HTTP ${res.status}`)
      }

      setIsModalOpen(false)
      fetchData()
    } catch (err: any) {
      alert(err.message || 'Error saving plan')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDeletePlan = async (planId: string) => {
    if (!confirm('Are you sure you want to delete this subscription plan?')) return
    try {
      const token = localStorage.getItem('nexus_jwt_token')
      const headers: Record<string, string> = {
        'X-Superadmin': 'true',
        'X-User-Role': 'SUPERADMIN'
      }
      if (token) headers['Authorization'] = `Bearer ${token}`

      const res = await fetch(`/api/payments/plans?id=${planId}`, {
        method: 'DELETE',
        headers
      })

      if (!res.ok) throw new Error('Failed to delete plan')
      fetchData()
    } catch (err: any) {
      alert(err.message || 'Error deleting plan')
    }
  }

  const filteredTransactions = transactions.filter(t => {
    const matchesStatus = statusFilter === 'ALL' || t.status === statusFilter
    const q = searchQuery.toLowerCase()
    const matchesQuery = !searchQuery || 
      t.tenantId.toLowerCase().includes(q) ||
      t.id.toLowerCase().includes(q) ||
      (t.tenantDisplayName && t.tenantDisplayName.toLowerCase().includes(q)) ||
      (t.paymobOrderId && t.paymobOrderId.toLowerCase().includes(q))
    return matchesStatus && matchesQuery
  })

  return (
    <SuperAdminLayout
      pageTitle="Subscriptions & Platform Billing"
      pageSubtitle="Monitor real-time EGP transaction revenue, manage multi-tenant subscription tiers, and configure Paymob integration IDs."
      onLogout={onLogout}
      headerActions={
        <div className="flex items-center gap-2">
          <button
            onClick={fetchData}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-[#E5E7EB] bg-white text-[#4B5563] text-xs font-semibold hover:bg-slate-50 transition-all shadow-sm"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
          <button
            onClick={handleOpenCreateModal}
            className="flex items-center gap-2 px-4 py-1.5 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1D4ED8] transition-all shadow-sm"
          >
            <Plus className="w-3.5 h-3.5" />
            New Subscription Plan
          </button>
        </div>
      }
    >
      {/* Error state alert */}
      {error && (
        <div className="mb-6 p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 flex items-center justify-between text-sm shadow-sm">
          <div className="flex items-center gap-3">
            <AlertCircle className="w-5 h-5 text-red-600 flex-shrink-0" />
            <div>
              <p className="font-semibold">Unable to connect to Payment Service</p>
              <p className="text-xs text-red-600 mt-0.5">{error}</p>
            </div>
          </div>
          <button onClick={fetchData} className="px-3 py-1 bg-red-100 hover:bg-red-200 text-red-800 rounded-lg text-xs font-medium transition-all">
            Retry
          </button>
        </div>
      )}

      {/* Stats Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-5 mb-8">
        <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-[#6B7280]">Total Revenue (This Month)</p>
            <h3 className="text-2xl font-bold text-[#1F2937] mt-1">
              {summary ? `${(summary.totalRevenuePiastersThisMonth / 100).toLocaleString('en-US', { minimumFractionDigits: 2 })} EGP` : '—'}
            </h3>
            <p className="text-[11px] text-[#16A34A] mt-1 font-medium flex items-center gap-1">
              <TrendingUp className="w-3 h-3" /> Live EGP Transactions
            </p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-[#F0FDF4] border border-[#DCFCE7] flex items-center justify-center text-[#16A34A]">
            <DollarSign className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-[#6B7280]">Active Subscriptions</p>
            <h3 className="text-2xl font-bold text-[#1F2937] mt-1">
              {summary ? summary.activeSubscriptions : '—'}
            </h3>
            <p className="text-[11px] text-[#2563EB] mt-1 font-medium flex items-center gap-1">
              <CheckCircle2 className="w-3 h-3" /> Active Tenants
            </p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-[#EFF6FF] border border-[#DBEAFE] flex items-center justify-center text-[#2563EB]">
            <ShieldCheck className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-[#E5E7EB] shadow-sm flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-[#6B7280]">Failed Payments</p>
            <h3 className="text-2xl font-bold text-[#1F2937] mt-1">
              {summary ? summary.failedPaymentsCount : '—'}
            </h3>
            <p className="text-[11px] text-[#DC2626] mt-1 font-medium flex items-center gap-1">
              <AlertCircle className="w-3 h-3" /> Failed Attempts
            </p>
          </div>
          <div className="w-12 h-12 rounded-xl bg-[#FEF2F2] border border-[#FEE2E2] flex items-center justify-center text-[#DC2626]">
            <CreditCard className="w-6 h-6" />
          </div>
        </div>
      </div>

      {/* Subscription Plans Section */}
      <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm mb-8 overflow-hidden">
        <div className="px-6 py-4 border-b border-[#E5E7EB] flex items-center justify-between bg-slate-50/50">
          <div>
            <h2 className="text-base font-bold text-[#1F2937]">Subscription Plans & Paymob Mappings</h2>
            <p className="text-xs text-[#6B7280] mt-0.5">Configured pricing tiers available for multi-tenant subscription purchases</p>
          </div>
        </div>

        <div className="p-6 grid grid-cols-1 md:grid-cols-3 gap-6">
          {plans.map((plan) => (
            <div key={plan.id} className="rounded-xl border border-[#E5E7EB] bg-white p-5 shadow-xs relative hover:border-[#BFDBFE] transition-all">
              <div className="flex items-start justify-between">
                <div>
                  <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-[#EFF6FF] text-[#2563EB] border border-[#BFDBFE]">
                    {plan.billingInterval}
                  </span>
                  <h3 className="text-lg font-bold text-[#1F2937] mt-2">{plan.name}</h3>
                </div>
                <div className="flex items-center gap-1">
                  <button onClick={() => handleOpenEditModal(plan)} className="p-1.5 text-[#6B7280] hover:text-[#2563EB] hover:bg-slate-100 rounded-lg transition-all">
                    <Edit2 className="w-4 h-4" />
                  </button>
                  <button onClick={() => handleDeletePlan(plan.id)} className="p-1.5 text-[#6B7280] hover:text-red-600 hover:bg-red-50 rounded-lg transition-all">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>

              <div className="mt-4 mb-4">
                <span className="text-2xl font-extrabold text-[#1F2937]">{(plan.pricePiasters / 100).toLocaleString()}</span>
                <span className="text-xs text-[#6B7280] font-medium ml-1">EGP / {plan.billingInterval.toLowerCase()}</span>
              </div>

              <div className="pt-3 border-t border-slate-100 text-[11px] text-[#6B7280] space-y-1">
                <p><span className="font-semibold text-slate-700">Integration ID:</span> {plan.integrationIds || '5834828'}</p>
                <p><span className="font-semibold text-slate-700">Plan ID:</span> <span className="font-mono text-[10px]">{plan.id}</span></p>
              </div>
            </div>
          ))}

          {plans.length === 0 && !loading && (
            <div className="col-span-3 text-center py-8 text-[#9CA3AF] text-sm">
              No subscription plans configured. Click "New Subscription Plan" to add default plans.
            </div>
          )}
        </div>
      </div>

      {/* Global Transactions History Table */}
      <div className="bg-white rounded-xl border border-[#E5E7EB] shadow-sm overflow-hidden">
        <div className="px-6 py-4 border-b border-[#E5E7EB] flex flex-col md:flex-row items-start md:items-center justify-between gap-4 bg-slate-50/50">
          <div>
            <h2 className="text-base font-bold text-[#1F2937]">Global Payment Transactions</h2>
            <p className="text-xs text-[#6B7280] mt-0.5">Audit log of all EGP checkout and recurring MOTO payment events</p>
          </div>

          <div className="flex items-center gap-3 w-full md:w-auto">
            {/* Search */}
            <div className="relative flex-1 md:w-64">
              <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-[#9CA3AF]" />
              <input
                type="text"
                placeholder="Search Company, Tenant ID, or Txn ID..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full h-8 pl-8 pr-3 bg-white border border-[#E5E7EB] rounded-lg text-xs outline-none focus:border-[#2563EB] transition-all"
              />
            </div>

            {/* Status Filter */}
            <div className="flex items-center gap-1.5 bg-white border border-[#E5E7EB] rounded-lg px-2.5 py-1">
              <Filter className="w-3.5 h-3.5 text-[#6B7280]" />
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="bg-transparent text-xs text-[#374151] font-medium outline-none cursor-pointer"
              >
                <option value="ALL">All Statuses</option>
                <option value="SUCCESS">Success</option>
                <option value="PENDING">Pending</option>
                <option value="FAILED">Failed</option>
              </select>
            </div>
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs text-[#374151]">
            <thead className="bg-[#F8FAFC] border-b border-[#E5E7EB] text-[#6B7280] font-semibold uppercase tracking-wider">
              <tr>
                <th className="px-6 py-3">Transaction ID / Date</th>
                <th className="px-6 py-3">Company / Tenant</th>
                <th className="px-6 py-3">Type</th>
                <th className="px-6 py-3">Amount (EGP)</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3">Paymob Order ID</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#E5E7EB]">
              {filteredTransactions.map((txn) => (
                <tr key={txn.id} className="hover:bg-slate-50/80 transition-colors">
                  <td className="px-6 py-3.5">
                    <div className="font-mono text-slate-800 font-medium">{txn.id.substring(0, 8)}...</div>
                    <div className="text-[10px] text-[#9CA3AF] mt-0.5">
                      {txn.createdAt ? new Date(txn.createdAt).toLocaleString() : 'Just now'}
                    </div>
                  </td>
                  <td className="px-6 py-3.5">
                    <div className="font-semibold text-slate-800 text-xs">
                      {txn.tenantDisplayName || 'Unknown Company'}
                    </div>
                    <div className="font-mono text-[10px] text-[#9CA3AF] mt-0.5" title={txn.tenantId}>
                      {txn.tenantId.substring(0, 8)}…{txn.tenantId.substring(txn.tenantId.length - 4)}
                    </div>
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
                        <CheckCircle2 className="w-3 h-3" /> Success
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
                  </td>
                  <td className="px-6 py-3.5 font-mono text-[11px] text-slate-500">
                    {txn.paymobOrderId || 'N/A'}
                  </td>
                </tr>
              ))}

              {filteredTransactions.length === 0 && !loading && (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-[#9CA3AF]">
                    No transactions found matching the selected filter criteria.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Subscription Plan Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl border border-[#E5E7EB] shadow-2xl max-w-md w-full overflow-hidden animate-in fade-in zoom-in duration-150">
            <div className="px-6 py-4 border-b border-[#E5E7EB] flex items-center justify-between bg-slate-50">
              <h3 className="font-bold text-[#1F2937]">
                {editingPlan ? 'Edit Subscription Plan' : 'Create New Subscription Plan'}
              </h3>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSavePlan} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Plan Name</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Starter, Business, Enterprise"
                  value={planForm.name}
                  onChange={(e) => setPlanForm({ ...planForm, name: e.target.value })}
                  className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm outline-none focus:border-[#2563EB] focus:ring-1 focus:ring-[#2563EB]"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Price (EGP)</label>
                <input
                  type="number"
                  required
                  min="1"
                  step="0.01"
                  value={planForm.priceEgp}
                  onChange={(e) => setPlanForm({ ...planForm, priceEgp: parseFloat(e.target.value) || 0 })}
                  className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm outline-none focus:border-[#2563EB] focus:ring-1 focus:ring-[#2563EB]"
                />
                <p className="text-[10px] text-slate-500 mt-1">Stored internally as piasters ({(planForm.priceEgp * 100).toLocaleString()} piasters)</p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Billing Interval</label>
                <select
                  value={planForm.billingInterval}
                  onChange={(e) => setPlanForm({ ...planForm, billingInterval: e.target.value })}
                  className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm outline-none focus:border-[#2563EB]"
                >
                  <option value="MONTHLY">MONTHLY</option>
                  <option value="YEARLY">YEARLY</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Paymob Integration IDs</label>
                <input
                  type="text"
                  required
                  value={planForm.integrationIds}
                  onChange={(e) => setPlanForm({ ...planForm, integrationIds: e.target.value })}
                  className="w-full px-3 py-2 border border-[#E5E7EB] rounded-lg text-sm outline-none focus:border-[#2563EB]"
                />
                <p className="text-[10px] text-slate-500 mt-1">Paymob Card Integration ID (e.g. 5834828)</p>
              </div>

              <div className="pt-4 flex items-center justify-end gap-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 rounded-lg border border-[#E5E7EB] text-slate-600 text-xs font-semibold hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1D4ED8] transition-all disabled:opacity-50"
                >
                  {isSubmitting ? 'Saving...' : 'Save Plan'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </SuperAdminLayout>
  )
}
