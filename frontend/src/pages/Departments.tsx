import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Select, Table, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { departmentApi, buildingApi, instituteApi } from '../services/api'
import type { Department, DepartmentRequest, Building, Institute } from '../types'
import { useToast } from '../contexts/ToastContext'

const EMPTY: DepartmentRequest = { name: '', code: '', instituteId: 0, buildingIds: [] }

export default function Departments() {
  const { toast } = useToast()
  const [items, setItems] = useState<Department[]>([])
  const [buildings, setBuildings] = useState<Building[]>([])
  const [institutes, setInstitutes] = useState<Institute[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Department | null>(null)
  const [form, setForm] = useState<DepartmentRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.allSettled([departmentApi.getAll(), buildingApi.getAll(), instituteApi.getAll()])
      .then(([deps, bldgs, insts]) => {
        if (deps.status === 'fulfilled') setItems(deps.value)
        if (bldgs.status === 'fulfilled') setBuildings(bldgs.value)
        if (insts.status === 'fulfilled') setInstitutes(insts.value)
        const failed = [deps, bldgs, insts].filter((x) => x.status === 'rejected').length
        if (failed > 0) toast.error(`Some department data failed to refresh (${failed}/3)`)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => {
    setEditing(null)
    setForm({ ...EMPTY, instituteId: institutes[0]?.id ?? 0 })
    setOpen(true)
  }
  const openEdit = (d: Department) => {
    setEditing(d)
    setForm({
      name: d.name,
      code: d.code,
      instituteId: d.instituteId ?? institutes[0]?.id ?? 0,
      buildingIds: (d.buildingsAllowed ?? []).map((b) => b.id),
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    if (!form.code.trim()) { toast.error('Code is required'); return }
    if (!form.instituteId) { toast.error('Please select an institute'); return }
    setSaving(true)
    try {
      if (editing) {
        const updated = await departmentApi.update(editing.id, form)
        setItems((prev) => prev.map((d) => (d.id === updated.id ? updated : d)))
        toast.success('Department updated')
      } else {
        const created = await departmentApi.create(form)
        setItems((prev) => [created, ...prev])
        toast.success('Department created')
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
      await departmentApi.delete(confirmId)
      setItems((prev) => prev.filter((d) => d.id !== confirmId))
      toast.success('Department deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const toggleBuilding = (id: number) => {
    setForm((prev) => {
      const current = prev.buildingIds ?? []
      return { ...prev, buildingIds: current.includes(id) ? current.filter((x) => x !== id) : [...current, id] }
    })
  }

  const instituteOptions = institutes.map((i) => ({ value: i.id, label: i.name }))

  const columns: Column<Department>[] = [
    {
      key: 'name', header: 'Name',
      sortValue: (d) => d.name,
      render: (d) => <span className="font-medium">{d.name}</span>,
    },
    {
      key: 'code', header: 'Code',
      sortValue: (d) => d.code,
      render: (d) => <code className="bg-gray-100 px-1 rounded text-xs">{d.code}</code>,
    },
    {
      key: 'institute', header: 'Institute',
      sortValue: (d) => d.instituteName ?? '',
      render: (d) => d.instituteName ?? <span className="text-gray-400">—</span>,
    },
    {
      key: 'buildings', header: 'Allowed Buildings',
      render: (d) => (d.buildingsAllowed && d.buildingsAllowed.length > 0)
        ? <span className="text-sm text-gray-600">{d.buildingsAllowed.map((b) => b.name).join(', ')}</span>
        : <span className="text-gray-400 text-sm">All buildings</span>,
    },
    {
      key: 'actions', header: '', width: '96px',
      render: (d) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(d)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(d.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (d: Department): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(d) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(d.id) },
  ]

  return (
    <>
      <Card
        title="Departments"
        description="Manage academic departments"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Department</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(d) => d.id}
          searchable
          exportable
          exportFilename="departments"
          searchKeys={[(d) => d.name, (d) => d.code]}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Department' : 'Add Department'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Computer Science" />
          <Input label="Code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="CS" helpText="Short identifier used in scheduling (e.g. CS, EE, ME)" />
          <Select label="Institute" value={form.instituteId} onChange={(e) => setForm({ ...form, instituteId: +e.target.value })} options={instituteOptions} placeholder="Select institute…" />

          <div>
            <p className="block text-sm font-medium text-gray-700 mb-2">
              Allowed Buildings <span className="font-normal text-gray-500">(used in scheduling to prefer department buildings)</span>
            </p>
            <div className="grid grid-cols-2 gap-2 border border-gray-200 rounded-md p-3">
              {buildings.map((b) => (
                <label key={b.id} className="flex items-center gap-2 text-sm cursor-pointer">
                  <input
                    type="checkbox"
                    checked={(form.buildingIds ?? []).includes(b.id)}
                    onChange={() => toggleBuilding(b.id)}
                  />
                  {b.name}{b.location ? ` (${b.location})` : ''}
                </label>
              ))}
              {buildings.length === 0 && <p className="text-sm text-gray-400 col-span-2">No buildings configured yet.</p>}
            </div>
          </div>
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Department"
        message="This will remove the department and all associated data. This cannot be undone."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}
