import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'
import CommandPalette from './CommandPalette'
import SettingsPanel from './SettingsPanel'

export default function Layout() {
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((open) => !open)
      }
    }
    const onOpen = () => setPaletteOpen(true)
    const onOpenSettings = () => setSettingsOpen(true)
    window.addEventListener('keydown', onKey)
    window.addEventListener('arare:open-command-palette', onOpen)
    window.addEventListener('arare:open-settings', onOpenSettings)
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('arare:open-command-palette', onOpen)
      window.removeEventListener('arare:open-settings', onOpenSettings)
    }
  }, [])

  return (
    <div className="flex min-h-screen bg-gray-50">
      <Sidebar />
      <div className="flex-1 flex flex-col min-w-0">
        <Header />
        <main className="flex-1 p-8">
          <Outlet />
        </main>
      </div>
      {paletteOpen && <CommandPalette onClose={() => setPaletteOpen(false)} />}
      <SettingsPanel open={settingsOpen} onClose={() => setSettingsOpen(false)} />
    </div>
  )
}
