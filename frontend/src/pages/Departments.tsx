import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Table } from '../components/ui'
import type { Column } from '../components/ui/Table'
import { departmentApi } from '../services/api'
import type { Department, DepartmentRequest } from '../types'

const EMPTY: DepartmentRequest = { name: '', code: '' }

export default function Departments() {
  const [items, setItems] = useState<Department[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Department | null>(null)
  const [form, setForm] = useState<DepartmentRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    departmentApi.getAll().then(setItems).finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setError(null); setOpen(true) }
  const openEdit = (d: Department) => {
    setEditing(d)
    setForm({ name: d.name, code: d.code })
    setError(null)
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { setError('Name is required'); return }
    if (!form.code.trim()) { setError('Code is required'); return }
    setSaving(true)
    try {
      if (editing) {
        await departmentApi.update(editing.id, form)
      } else {
        await departmentApi.create(form)
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
    if (!window.confirm('Delete this department?')) return
    try {
      await departmentApi.delete(id)
      load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'An error occurred')
    }
  }

  const columns: Column<Department>[] = [
    { key: 'name', header: 'Name', render: (d) => <span className="font-medium">{d.name}</span> },
    { key: 'code', header: 'Code', render: (d) => <code className="bg-gray-100 px-1 rounded text-xs">{d.code}</code> },
    {
      key: 'actions', header: '', width: '96px',
      render: (d) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(d)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => handleDelete(d.id)}>Delete</Button>
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
      <Card
        title="Departments"
        description="Manage academic departments"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Department</Button>}
      >
        <Table columns={columns} data={items} loading={loading} keyExtractor={(d) => d.id} />
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
          {error && (
            <div className="rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}
          <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Computer Science" />
          <Input label="Code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="CS" helpText="Short identifier used in scheduling (e.g. CS, EE, ME)" />
        </div>
      </Modal>
    </>
  )
}
