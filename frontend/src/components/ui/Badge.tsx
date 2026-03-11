type Variant = 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple'

const variantClasses: Record<Variant, string> = {
  gray:   'bg-gray-100 text-gray-700',
  green:  'bg-green-100 text-green-800',
  yellow: 'bg-yellow-100 text-yellow-800',
  red:    'bg-red-100 text-red-700',
  blue:   'bg-blue-100 text-blue-800',
  purple: 'bg-purple-100 text-purple-800',
}

interface BadgeProps {
  label: string
  variant?: Variant
  dot?: boolean
}

export default function Badge({ label, variant = 'gray', dot = false }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${variantClasses[variant]}`}
    >
      {dot && <span className="w-1.5 h-1.5 rounded-full bg-current" />}
      {label}
    </span>
  )
}
