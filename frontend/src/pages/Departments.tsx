import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Table, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { departmentApi, buildingApi } from '../services/api'
import type { Department, DepartmentRequest, Building } from '../types'
import { useToast } from '../contexts/ToastContext'

const EMPTY: DepartmentRequest = { name: '', code: '', buildingIds: [] }

export default function Departments() {
  const { toast } = useToast()
  const [items, setItems] = useState<Department[]>([])
  const [buildings, setBuildings] = useState<Building[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Department | null>(null)
  const [form, setForm] = useState<DepartmentRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    Promise.all([departmentApi.getAll(), buildingApi.getAll()])
      .then(([deps, bldgs]) => { setItems(deps); setBuildings(bldgs) })
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setOpen(true) }
  const openEdit = (d: Department) => {
    setEditing(d)
    setForm({
      name: d.name,
      code: d.code,
      buildingIds: (d.buildingsAllowed ?? []).map((b) => b.id),
    })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    if (!form.code.trim()) { toast.error('Code is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await departmentApi.update(editing.id, form)
        toast.success('Department updated')
      } else {
        await departmentApi.create(form)
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
