import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, Badge, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { roomApi, buildingApi, timeslotApi } from '../services/api'
import type { Room, RoomRequest, Building, Timeslot, RoomType, LabSubtype, SchoolDay } from '../types'
import { useToast } from '../contexts/ToastContext'

const LAB_SUBTYPES: LabSubtype[] = [
  'COMPUTER_LAB', 'ELECTRONICS_LAB', 'CHEMISTRY_LAB', 'PHYSICS_LAB',
  'MECHANICAL_LAB', 'CIVIL_LAB', 'NETWORK_LAB', 'GENERAL_LAB',
]

const DAYS: SchoolDay[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']

const EMPTY: RoomRequest = { buildingId: 0, roomNumber: '', type: 'LECTURE', capacity: 30, availableTimeslotIds: [] }

export default function Rooms() {
  const { toast } = useToast()
  const [items, setItems] = useState<Room[]>([])
  const [buildings, setBuildings] = useState<Building[]>([])
  const [timeslots, setTimeslots] = useState<Timeslot[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Room | null>(null)
  const [form, setForm] = useState<RoomRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([roomApi.getAll(), buildingApi.getAll(), timeslotApi.getAll()])
      .then(([rooms, bldgs, ts]) => { setItems(rooms); setBuildings(bldgs); setTimeslots(ts) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, buildingId: buildings[0]?.id ?? 0 })
    setOpen(true)
  }
  const openEdit = (r: Room) => {
    setEditing(r)
    setForm({ buildingId: r.buildingId, roomNumber: r.roomNumber, type: r.type, labSubtype: r.labSubtype, capacity: r.capacity, availableTimeslotIds: r.availableTimeslotIds ?? [] })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.roomNumber.trim()) { toast.error('Room number is required'); return }
    if (!form.buildingId) { toast.error('Please select a building'); return }
    setSaving(true)
    try {
      const data: RoomRequest = { ...form, labSubtype: form.type === 'LAB' ? form.labSubtype : undefined }
      if (editing) {
        await roomApi.update(editing.id, data)
        toast.success('Room updated')
      } else {
        await roomApi.create(data)
        toast.success('Room created')
      }
      setOpen(false)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'An error occurred')
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (confirmId == null) return
    setDeleting(true)
    try {
      await roomApi.delete(confirmId)
      toast.success('Room deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const toggleTimeslot = (id: number) => {
    setForm((prev) => {
      const current = prev.availableTimeslotIds ?? []
      return {
        ...prev,
        availableTimeslotIds: current.includes(id) ? current.filter((x) => x !== id) : [...current, id],
      }
    })
  }

  const bldgOptions = buildings.map((b) => ({ value: b.id, label: b.name }))
  const typeOptions: { value: string; label: string }[] = [
    { value: 'LECTURE', label: 'Lecture Hall' },
    { value: 'LAB', label: 'Laboratory' },
  ]
  const subtypeOptions = LAB_SUBTYPES.map((s) => ({ value: s, label: s.replace(/_/g, ' ') }))

  const timeslotsByDay = DAYS.reduce<Record<SchoolDay, Timeslot[]>>((acc, day) => {
    acc[day] = timeslots.filter((t) => t.day === day && t.type === 'CLASS')
    return acc
  }, {} as Record<SchoolDay, Timeslot[]>)

  const columns: Column<Room>[] = [
    {
      key: 'roomNumber', header: 'Room No.',
      sortValue: (r) => r.roomNumber,
      render: (r) => <span className="font-medium">{r.roomNumber}</span>,
    },
    {
      key: 'building', header: 'Building',
      sortValue: (r) => r.buildingName ?? '',
      render: (r) => r.buildingName ?? `#${r.buildingId}`,
    },
    { key: 'capacity', header: 'Capacity', render: (r) => r.capacity },
    { key: 'type', header: 'Type', render: (r) => <Badge label={r.type} variant={r.type === 'LAB' ? 'purple' : 'blue'} /> },
    { key: 'labSubtype', header: 'Lab Type', render: (r) => r.labSubtype ? <span className="text-xs text-gray-600">{r.labSubtype.replace(/_/g, ' ')}</span> : <span className="text-gray-400">—</span> },
    {
      key: 'actions', header: '', width: '96px',
      render: (r) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(r)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(r.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (r: Room): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(r) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(r.id) },
  ]

  return (
    <>
      <Card title="Rooms" description="Manage classrooms and labs"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Room</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(r) => r.id}
          searchable
          searchKeys={[(r) => r.roomNumber, (r) => r.buildingName ?? '']}
          onRowContextMenu={getContextItems}
        />
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
          <div className="grid grid-cols-2 gap-4">
            <Input label="Room Number" value={form.roomNumber} onChange={(e) => setForm({ ...form, roomNumber: e.target.value })} placeholder="101" />
            <Input label="Capacity" type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: +e.target.value })} />
            <Select label="Building" value={form.buildingId} onChange={(e) => setForm({ ...form, buildingId: +e.target.value })} options={bldgOptions} placeholder="Select building…" />
            <Select label="Type" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as RoomType, labSubtype: undefined })} options={typeOptions} />
          </div>
          {form.type === 'LAB' && (
            <Select label="Lab Subtype" value={form.labSubtype ?? ''} onChange={(e) => setForm({ ...form, labSubtype: e.target.value as LabSubtype })} options={subtypeOptions} placeholder="Select lab type…" />
          )}

          <div>
            <p className="block text-sm font-medium text-gray-700 mb-1">
              Availability <span className="font-normal text-gray-500">(leave all unchecked = always available)</span>
            </p>
            <p className="text-xs text-gray-500 mb-2">Select timeslots only when this room is restricted or under maintenance</p>
            <div className="border border-gray-200 rounded-md p-3 max-h-48 overflow-y-auto space-y-3">
              {DAYS.map((day) => {
                const slots = timeslotsByDay[day]
                if (!slots.length) return null
                return (
                  <div key={day}>
                    <p className="text-xs font-semibold text-gray-500 uppercase mb-1">{day}</p>
                    <div className="flex flex-wrap gap-2">
                      {slots.map((ts) => (
                        <label key={ts.id} className="flex items-center gap-1 text-xs cursor-pointer bg-gray-50 border border-gray-200 rounded px-2 py-1">
                          <input
                            type="checkbox"
                            checked={(form.availableTimeslotIds ?? []).includes(ts.id)}
                            onChange={() => toggleTimeslot(ts.id)}
                          />
                          {ts.startTime}–{ts.endTime}
                        </label>
                      ))}
                    </div>
                  </div>
                )
              })}
              {timeslots.filter((t) => t.type === 'CLASS').length === 0 && (
                <p className="text-sm text-gray-400">No CLASS timeslots configured yet.</p>
              )}
            </div>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Room"
        message="This will remove the room and clear related session assignments. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
