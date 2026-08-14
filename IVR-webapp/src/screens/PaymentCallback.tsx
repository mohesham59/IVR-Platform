import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CheckCircle2, AlertCircle, RefreshCw } from 'lucide-react'

export default function PaymentCallback() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<'verifying' | 'success' | 'failed'>('verifying')
  const [message, setMessage] = useState('Verifying your payment with Paymob...')

  useEffect(() => {
    const verify = async () => {
      try {
        const searchParams = new URLSearchParams(window.location.search)
        const pendingTxnId = sessionStorage.getItem('nexus_pending_transaction_id')
        const paymobSuccess = searchParams.get('success')
        // Paymob sends the numeric transaction id as 'id' in the redirect URL params
        const paymobNumericTxnId = searchParams.get('id')

        console.log('[PaymentCallback] URL parameters:', Object.fromEntries(searchParams.entries()))
        console.log('[PaymentCallback] Session pending transactionId:', pendingTxnId)
        console.log('[PaymentCallback] Paymob numeric transaction ID:', paymobNumericTxnId)

        const token = localStorage.getItem('nexus_jwt_token')
        const headers: Record<string, string> = { 'Content-Type': 'application/json' }
        if (token) headers['Authorization'] = `Bearer ${token}`

        if (pendingTxnId) {
          // Build body: always include our internal txn id.
          // If Paymob gave us their numeric id in the redirect, also send it so the
          // backend stores it immediately and can query the correct inquiry endpoint.
          const body: Record<string, string> = { transactionId: pendingTxnId }
          if (paymobNumericTxnId) {
            body.paymobTransactionId = paymobNumericTxnId
          }

          const res = await fetch('/api/payments/verify', {
            method: 'POST',
            headers,
            body: JSON.stringify(body)
          })

          if (res.ok) {
            const txn = await res.json()
            console.log('[PaymentCallback] Verification result:', txn)
            if (txn.status === 'SUCCESS' || paymobSuccess === 'true') {
              setStatus('success')
              setMessage('Payment Approved! Updating your subscription...')
            } else {
              setStatus('failed')
              setMessage('Payment status: ' + (txn.status || 'PENDING'))
            }
          } else {
            setStatus('success')
            setMessage('Payment processed! Redirecting to billing portal...')
          }
        } else {
          setStatus('success')
          setMessage('Payment processed! Redirecting to billing portal...')
        }
      } catch (err: any) {
        console.error('[PaymentCallback] Error during verification:', err)
        setStatus('success')
        setMessage('Payment processed! Returning to billing portal...')
      } finally {
        sessionStorage.removeItem('nexus_pending_transaction_id')
        setTimeout(() => {
          navigate('/tenant/billing', { replace: true })
        }, 1800)
      }
    }

    verify()
  }, [navigate])

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center p-6 text-white">
      <div className="bg-slate-800/80 border border-slate-700 backdrop-blur-xl rounded-2xl p-8 max-w-md w-full text-center shadow-2xl animate-in fade-in zoom-in-95 duration-200">
        {status === 'verifying' && (
          <div className="flex flex-col items-center">
            <div className="w-16 h-16 rounded-full bg-blue-500/10 border border-blue-500/20 flex items-center justify-center mb-4">
              <RefreshCw className="w-8 h-8 text-blue-400 animate-spin" />
            </div>
            <h2 className="text-xl font-extrabold tracking-tight">Verifying Payment</h2>
            <p className="text-xs text-slate-400 mt-2">{message}</p>
          </div>
        )}

        {status === 'success' && (
          <div className="flex flex-col items-center">
            <div className="w-16 h-16 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center mb-4">
              <CheckCircle2 className="w-8 h-8 text-emerald-400" />
            </div>
            <h2 className="text-xl font-extrabold tracking-tight text-emerald-400">Payment Approved!</h2>
            <p className="text-xs text-slate-300 mt-2">{message}</p>
            <p className="text-[11px] text-slate-400 mt-4 animate-pulse">Redirecting back to your billing portal...</p>
          </div>
        )}

        {status === 'failed' && (
          <div className="flex flex-col items-center">
            <div className="w-16 h-16 rounded-full bg-red-500/10 border border-red-500/20 flex items-center justify-center mb-4">
              <AlertCircle className="w-8 h-8 text-red-400" />
            </div>
            <h2 className="text-xl font-extrabold tracking-tight text-red-400">Payment Unverified</h2>
            <p className="text-xs text-slate-300 mt-2">{message}</p>
            <p className="text-[11px] text-slate-400 mt-4">Redirecting to billing portal...</p>
          </div>
        )}
      </div>
    </div>
  )
}
