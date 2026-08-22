import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'

export type FeatureKey =
  | 'analytics'
  | 'manualSessions'
  | 'whatIf'
  | 'academicTerms'
  | 'events'
  | 'disruptions'
  | 'importExport'

export type UiPreferences = Record<FeatureKey, boolean>

export interface FeatureDef {
  key: FeatureKey
  label: string
  description: string
}

export const FEATURES: FeatureDef[] = [
  { key: 'analytics', label: 'Analytics', description: 'Dashboard analytics and charts' },
  { key: 'manualSessions', label: 'Manual Sessions', description: 'Manually add or edit sessions outside the solver' },
  { key: 'whatIf', label: 'What-If Compare', description: 'Compare schedule scenarios before committing' },
  { key: 'academicTerms', label: 'Academic Terms', description: 'Manage academic terms' },
  { key: 'events', label: 'Events', description: 'Manage events and special days' },
  { key: 'disruptions', label: 'Disruptions', description: 'Handle disruptions and re-planning' },
  { key: 'importExport', label: 'Import / Export', description: 'CSV import and export' },
]

const STORAGE_KEY = 'arare.ui-preferences'

const DEFAULTS: UiPreferences = {
  analytics: true,
  manualSessions: true,
  whatIf: true,
  academicTerms: true,
  events: true,
  disruptions: true,
  importExport: true,
}

interface UiPreferencesContextValue {
  prefs: UiPreferences
  setEnabled: (key: FeatureKey, enabled: boolean) => void
  reset: () => void
}

const UiPreferencesContext = createContext<UiPreferencesContextValue | null>(null)

function load(): UiPreferences {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULTS
    const parsed = JSON.parse(raw)
    return { ...DEFAULTS, ...parsed }
  } catch {
    return DEFAULTS
  }
}

export function UiPreferencesProvider({ children }: { children: ReactNode }) {
  const [prefs, setPrefs] = useState<UiPreferences>(load)

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(prefs))
    } catch {
      // storage unavailable — keep in-memory only
    }
  }, [prefs])

  const setEnabled = useCallback((key: FeatureKey, enabled: boolean) => {
    setPrefs((prev) => ({ ...prev, [key]: enabled }))
  }, [])

  const reset = useCallback(() => setPrefs(DEFAULTS), [])

  return (
    <UiPreferencesContext.Provider value={{ prefs, setEnabled, reset }}>
      {children}
    </UiPreferencesContext.Provider>
  )
}

export function useUiPreferences() {
  const ctx = useContext(UiPreferencesContext)
  if (!ctx) throw new Error('useUiPreferences must be used within UiPreferencesProvider')
  return ctx
}