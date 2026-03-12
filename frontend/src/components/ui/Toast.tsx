import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react'
import { createPortal } from 'react-dom'
import { useToast, type ToastVariant } from '../../contexts/ToastContext'

const VARIANT_STYLES: Record<ToastVariant, { bg: string; border: string; text: string; icon: JSX.Element }> = {
  success: {
    bg: 'bg-green-50',
    border: 'border-green-300',
    text: 'text-green-800',
    icon: <CheckCircle size={16} className="text-green-500 shrink-0" />,
  },
  error: {
    bg: 'bg-red-50',
    border: 'border-red-300',
    text: 'text-red-800',
    icon: <XCircle size={16} className="text-red-500 shrink-0" />,
  },
  warning: {
    bg: 'bg-amber-50',
    border: 'border-amber-300',
    text: 'text-amber-800',
    icon: <AlertTriangle size={16} className="text-amber-500 shrink-0" />,
  },
  info: {
    bg: 'bg-blue-50',
    border: 'border-blue-300',
    text: 'text-blue-800',
    icon: <Info size={16} className="text-blue-500 shrink-0" />,
  },
}

export default function ToastContainer() {
  const { toasts, dismiss } = useToast()

  if (toasts.length === 0) return null

  return createPortal(
    <div className="fixed bottom-5 right-5 z-[9999] flex flex-col gap-2 max-w-sm">
      {toasts.map((t) => {
        const s = VARIANT_STYLES[t.variant]
        return (
          <div
            key={t.id}
            className={`flex items-start gap-3 rounded-lg border px-4 py-3 shadow-lg animate-slide-in ${s.bg} ${s.border}`}
          >
            {s.icon}
            <p className={`flex-1 text-sm font-medium ${s.text}`}>{t.message}</p>
            <button
              onClick={() => dismiss(t.id)}
              className={`p-0.5 rounded hover:bg-black/10 transition-colors ${s.text}`}
            >
              <X size={14} />
            </button>
          </div>
        )
      })}
    </div>,
    document.body,
  )
}
