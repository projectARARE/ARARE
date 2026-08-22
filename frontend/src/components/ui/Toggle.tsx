interface ToggleProps {
  label: string
  checked: boolean
  onChange: (value: boolean) => void
  helpText?: string
  disabled?: boolean
}

const Toggle = ({ label, checked, onChange, helpText, disabled }: ToggleProps) => (
  <div className="space-y-1">
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`
        flex items-center gap-3 rounded-md border px-3 py-2 text-sm shadow-sm transition-colors
        focus:outline-none focus:ring-2 focus:ring-primary-500
        ${checked ? 'border-primary-300 bg-primary-50 text-primary-700' : 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'}
        disabled:cursor-not-allowed disabled:opacity-50
      `}
    >
      <span
        className={`
          relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors
          ${checked ? 'bg-primary-500' : 'bg-gray-300'}
        `}
      >
        <span
          className={`
            inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform
            ${checked ? 'translate-x-4' : 'translate-x-0.5'}
          `}
        />
      </span>
      <span className="font-medium">{label}</span>
    </button>
    {helpText && <p className="text-xs text-gray-500">{helpText}</p>}
  </div>
)

export default Toggle