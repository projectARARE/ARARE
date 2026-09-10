import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog, FilterPanel, SearchableSelect } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { buildingApi, instituteApi } from '../services/api'
import type { Building, BuildingRequest, Institute } from '../types'
import { useToast } from '../contexts/ToastContext'
import { useBulkDelete } from '../hooks/useBulkDelete'

const EMPTY: BuildingRequest = { name: '', location: '' }

export default function Buildings() {
  const { toast } = useToast()
  const [items, setItems] = useState<Building[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Building | null>(null)
  const [form, setForm] = useState<BuildingRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [locationFilter, setLocationFilter] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    Promise.allSettled([buildingApi.getAll(), instituteApi.getAll()])
      .then(([b, i]) => {
        if (b.status === 'fulfilled') setItems(b.value)
        if (i.status === 'fulfilled') setInstitutes(i.value)
        const failed = [b, i].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some data failed to refresh (${failed}/2)`)
      })
      .catch((e) => toast.error(e instanceof Error ? e.message : 'Failed to load buildings'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm({ ...EMPTY, instituteId: institutes.length === 1 ? institutes[0].id : undefined }); setOpen(true) }
  const openEdit = (b: Building) => {
    setEditing(b)
    setForm({ name: b.name, location: b.location ?? '', instituteId: b.instituteId })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    setSaving(true)
    try {
      if (editing) {
        const updated = await buildingApi.update(editing.id, form)
        setItems((prev) => prev.map((b) => (b.id === updated.id ? updated : b)))
        toast.success('Building updated')
      } else {
        const created = await buildingApi.create(form)
        setItems((prev) => [created, ...prev])
        toast.success('Building created')
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
      await buildingApi.delete(confirmId)
      setItems((prev) => prev.filter((b) => b.id !== confirmId))
      toast.success('Building deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const columns: Column<Building>[] = [
    {
      key: 'name', header: 'Name',
      sortValue: (b) => b.name,
      render: (b) => <span className="font-medium">{b.name}</span>,
    },
    { key: 'location', header: 'Location', render: (b) => b.location ?? '—' },
    {
      key: 'actions', header: '', width: '96px',
      render: (b) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} className="px-1.5" onClick={() => openEdit(b)} title="Edit" aria-label="Edit" />
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600 hover:text-red-700 px-1.5" onClick={() => setConfirmId(b.id)} title="Delete" aria-label="Delete" />
        </div>
      ),
    },
  ]

  const getContextItems = (b: Building): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(b) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(b.id) },
  ]

  const bulk = useBulkDelete<Building>({
    getKey: (x) => x.id,
    deleteFn: buildingApi.delete,
    reload: load,
    noun: 'building',
  })

  const locationOptions = [
    { value: '', label: 'All locations' },
    ...Array.from(new Set(items.map((b) => b.location).filter((l): l is string => !!l)))
      .sort()
      .map((l) => ({ value: l, label: l })),
  ]

  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))

  const filteredItems = locationFilter ? items.filter((b) => b.location === locationFilter) : items

  return (
    <>
      <Card
        title="Buildings"
        description="Manage campus buildings"
        actions={<div className="flex items-center gap-2">{bulk.Bar}<Button icon={<Plus size={16} />} onClick={openAdd}>Add Building</Button></div>}
      >
        <FilterPanel activeCount={locationFilter != null ? 1 : 0} persistKey="arare.buildings.filters.open">
          <Select
            label="Location"
            value={locationFilter ?? ''}
            onChange={(e) => setLocationFilter(e.target.value || null)}
            options={locationOptions}
          />
        </FilterPanel>
        <Table
          columns={columns}
          data={filteredItems}
          loading={loading}
          keyExtractor={(b) => b.id}
          searchable
          exportable
          exportFilename="buildings"
          searchKeys={[(b) => b.name, (b) => b.location ?? '']}
          onRowContextMenu={getContextItems}
          selectable={bulk.selectable}
          onSelectionChange={bulk.onSelectionChange}
          clearSignal={bulk.clearSignal}
          densityStorageKey="arare.buildings.density"
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Building' : 'Add Building'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Engineering Block" required />
          <Input label="Location" value={form.location ?? ''} onChange={(e) => setForm({ ...form, location: e.target.value })} placeholder="North Campus, Block A" helpText="Human-readable campus location (optional)" />
          <SearchableSelect label="Institute" value={form.instituteId ?? null} onChange={(v) => setForm({ ...form, instituteId: v == null ? undefined : +v })} options={instituteOptions} placeholder="Select institute…" allowClear />
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Building"
        message="This will also remove all rooms and clear related session assignments. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
      {bulk.Dialog}
    </>
  )
}
