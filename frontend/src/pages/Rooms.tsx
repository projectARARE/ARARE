import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { roomApi, buildingApi } from '../services/api'
import type { Room, RoomRequest, Building, RoomType, LabSubtype } from '../types'

const LAB_SUBTYPES: LabSubtype[] = [
  'COMPUTER_LAB', 'ELECTRONICS_LAB', 'CHEMISTRY_LAB', 'PHYSICS_LAB',
  'MECHANICAL_LAB', 'CIVIL_LAB', 'NETWORK_LAB', 'GENERAL_LAB',
]

const EMPTY: RoomRequest = { buildingId: 0, roomNumber: '', type: 'LECTURE', capacity: 30 }

export default function Rooms() {
  const [items, setItems] = useState<Room[]>([])
  const [buildings, setBuildings] = useState<Building[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Room | null>(null)
  const [form, setForm] = useState<RoomRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.all([roomApi.getAll(), buildingApi.getAll()])
      .then(([rooms, bldgs]) => { setItems(rooms); setBuildings(bldgs) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, buildingId: buildings[0]?.id ?? 0 })
    setError(null)
    setOpen(true)
  }
  const openEdit = (r: Room) => {
    setEditing(r)
    setForm({ buildingId: r.buildingId, roomNumber: r.roomNumber, type: r.type, labSubtype: r.labSubtype, capacity: r.capacity })
    setError(null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.roomNumber.trim()) { setError('Room number is required'); return }
    if (!form.buildingId) { setError('Please select a building'); return }
    setSaving(true)
    try {
      const data: RoomRequest = { ...form, labSubtype: form.type === 'LAB' ? form.labSubtype : undefined }
      if (editing) {
        await roomApi.update(editing.id, data)
      } else {
        await roomApi.create(data)
      }
      setOpen(false)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: number) => {
    if (!window.confirm('Delete this room?')) return
    try {
      await roomApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const bldgOptions = buildings.map((b) => ({ value: b.id, label: b.name }))
  const typeOptions: { value: string; label: string }[] = [
    { value: 'LECTURE', label: 'Lecture Hall' },
    { value: 'LAB', label: 'Laboratory' },
  ]
  const subtypeOptions = LAB_SUBTYPES.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))

  const columns: Column<Room>[] = [
    { key: 'roomNumber', header: 'Room No.', render: (r) => <span className="font-medium">{r.roomNumber}</span> },
    { key: 'building', header: 'Building', render: (r) => r.buildingName ?? `#${r.buildingId}` },
    { key: 'capacity', header: 'Capacity', render: (r) => r.capacity },
    { key: 'type', header: 'Type', render: (r) => <Badge label={r.type} variant={r.type === 'LAB' ? 'purple' : 'blue'} /> },
    { key: 'labSubtype', header: 'Lab Type', render: (r) => r.labSubtype ? <span className="text-xs text-gray-600">{r.labSubtype.replace(/_/g, ' ')}</span> : <span className="text-gray-400">—</span> },
    {
      key: 'actions', header: '', width: '96px',
      render: (r) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(r)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => handleDelete(r.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  return (
    <>
      {error && !open && (
        <div className="mb-4 rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}
      <Card title="Rooms" description="Manage classrooms and labs"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Room</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(r) => r.id} />
      </Card>

      <Modal open={open} onClose={() => setOpen(false)} title={editing ? 'Edit Room' : 'Add Room'} size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          {error && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
          <div className="grid grid-cols-2 gap-4">
            <Input label="Room Number" value={form.roomNumber} onChange={(e) => setForm({ ...form, roomNumber: e.target.value })} placeholder="101" />
            <Input label="Capacity" type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: +e.target.value })} />
            <Select label="Building" value={form.buildingId} onChange={(e) => setForm({ ...form, buildingId: +e.target.value })} options={bldgOptions} placeholder="Select building…" />
            <Select label="Type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as RoomType, labSubtype: undefined })} options={typeOptions} />
          </div>
          {form.type === 'LAB' && (
            <Select label="Lab Subtype" value={form.labSubtype ?? ''} onChange={(e) => setForm({ ...form, labSubtype: e.target.value as LabSubtype })} options={subtypeOptions} placeholder="Select lab type…" />
          )}
        </div>
      </Modal>
    </>
  )
}
