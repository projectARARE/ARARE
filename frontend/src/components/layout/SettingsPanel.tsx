import { RotateCcw } from 'lucide-react'
import { Card, Modal, Toggle, Button } from '../ui'
import { FEATURES, useUiPreferences } from '../../contexts/UiPreferencesContext'

interface SettingsPanelProps {
  open: boolean
  onClose: () => void
}

export default function SettingsPanel({ open, onClose }: SettingsPanelProps) {
  const { prefs, setEnabled, reset } = useUiPreferences()

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Settings"
      size="lg"
      footer={
        <Button variant="secondary" icon={<RotateCcw size={15} />} onClick={reset}>
          Reset to defaults
        </Button>
      }
    >
      <div className="space-y-4">
        <div className="rounded-md bg-gray-50 border border-gray-200 px-4 py-3 text-sm text-gray-600">
          Enable or disable optional features. Disabled features are hidden from the sidebar and
          start pages so only what you need stays visible.
        </div>

        <Card title="Optional Features" className="bg-white border-gray-200 text-gray-900">
          <div className="space-y-3">
            {FEATURES.map((feature) => (
              <Toggle
                key={feature.key}
                label={feature.label}
                checked={prefs[feature.key]}
                onChange={(value) => setEnabled(feature.key, value)}
                helpText={feature.description}
              />
            ))}
          </div>
        </Card>
      </div>
    </Modal>
  )
}