import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

export interface ContextMenuItem {
  label: string
  icon?: ReactNode
  onClick: () => void
  danger?: boolean
  disabled?: boolean
  divider?: boolean
}

interface ContextMenuProps {
  x: number
  y: number
  items: ContextMenuItem[]
  onClose: () => void
}

export default function ContextMenu({ x, y, items, onClose }: ContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handle = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent && e.key !== 'Escape') return
      if (e instanceof MouseEvent && menuRef.current?.contains(e.target as Node)) return
      onClose()
    }
    document.addEventListener('mousedown', handle)
    document.addEventListener('keydown', handle)
    return () => {
      document.removeEventListener('mousedown', handle)
      document.removeEventListener('keydown', handle)
    }
  }, [onClose])

  // Adjust position so menu stays within viewport
  const style: React.CSSProperties = { position: 'fixed', left: x, top: y, zIndex: 9998 }

  return createPortal(
    <div
      ref={menuRef}
      style={style}
      className="min-w-[180px] bg-white rounded-lg shadow-xl border border-gray-200 py-1 overflow-hidden"
    >
      {items.map((item, i) => (
        <div key={i}>
          {item.divider && i > 0 && <hr className="my-1 border-gray-100" />}
          <button
            disabled={item.disabled}
            onClick={() => { item.onClick(); onClose() }}
            className={`w-full flex items-center gap-2.5 px-4 py-2 text-sm text-left transition-colors
              disabled:opacity-40 disabled:cursor-not-allowed
              ${item.danger
                ? 'text-red-600 hover:bg-red-50'
                : 'text-gray-700 hover:bg-gray-50'
              }
            `}
          >
            {item.icon && <span className="w-4 h-4 flex items-center justify-center">{item.icon}</span>}
            {item.label}
          </button>
        </div>
      ))}
    </div>,
    document.body,
  )
}
