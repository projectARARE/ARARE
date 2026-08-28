import { useState, useEffect } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, Button, Modal, Input, Table, ConfirmDialog } from '../components/ui'
import type { Column } from '../components/ui/Table'
import type { ContextMenuItem } from '../components/ui/ContextMenu'
import { instituteApi } from '../services/api'
import type { Institute, InstituteRequest } from '../types'
import { useToast } from '../contexts/ToastContext'

const EMPTY: InstituteRequest = { name: '', code: '', description: '' }

export default function Institutes() {
  const { toast } = useToast()
  const [items, setItems] = useState<Institute[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Institute | null>(null)
  const [form, setForm] = useState<InstituteRequest>(EMPTY)
  const [saving, setSaving] = useState(false)
  const [confirmId, setConfirmId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState(false)

  const load = () => {
    setLoading(true)
    instituteApi.getAll()
      .then(setItems)
      .catch(() => toast.error('Failed to load institutes'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const openAdd = () => { setEditing(null); setForm(EMPTY); setOpen(true) }
  const openEdit = (i: Institute) => {
    setEditing(i)
    setForm({ name: i.name, code: i.code, description: i.description ?? '' })
    setOpen(true)
  }

  const handleSave = async () => {
    if (!form.name.trim()) { toast.error('Name is required'); return }
    if (!form.code.trim()) { toast.error('Code is required'); return }
    setSaving(true)
    try {
      if (editing) {
        const updated = await instituteApi.update(editing.id, form)
        setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)))
        toast.success('Institute updated')
      } else {
        const created = await instituteApi.create(form)
        setItems((prev) => [...prev, created])
        toast.success('Institute created')
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
      await instituteApi.delete(confirmId)
      setItems((prev) => prev.filter((i) => i.id !== confirmId))
      toast.success('Institute deleted')
      setConfirmId(null)
      load()
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Delete failed')
      setConfirmId(null)
    } finally {
      setDeleting(false)
    }
  }

  const columns: Column<Institute>[] = [
    {
      key: 'name', header: 'Name',
      sortValue: (i) => i.name,
      render: (i) => <span className="font-medium">{i.name}</span>,
    },
    {
      key: 'code', header: 'Code',
      sortValue: (i) => i.code,
      render: (i) => <code className="bg-gray-100 px-1 rounded text-xs">{i.code}</code>,
    },
    {
      key: 'departments', header: 'Departments',
      sortValue: (i) => i.departmentCount,
      render: (i) => `${i.departmentCount}`,
    },
    {
      key: 'description', header: 'Description',
      render: (i) => i.description ?? <span className="text-gray-400">—</span>,
    },
    {
      key: 'actions', header: '', width: '96px',
      render: (i) => (
        <div className="flex gap-2">
          <Button variant="ghost" size="sm" icon={<Pencil size={14} />} onClick={() => openEdit(i)}>Edit</Button>
          <Button variant="ghost" size="sm" icon={<Trash2 size={14} />} className="text-red-600" onClick={() => setConfirmId(i.id)}>Delete</Button>
        </div>
      ),
    },
  ]

  const getContextItems = (i: Institute): ContextMenuItem[] => [
    { label: 'Edit', icon: <Pencil size={13} />, onClick: () => openEdit(i) },
    { label: 'Delete', icon: <Trash2 size={13} />, danger: true, divider: true, onClick: () => setConfirmId(i.id) },
  ]

  return (
    <>
      <Card
        title="Institutes"
        description="Constituent institutes within the university"
        actions={<Button icon={<Plus size={16} />} onClick={openAdd}>Add Institute</Button>}
      >
        <Table
          columns={columns}
          data={items}
          loading={loading}
          keyExtractor={(i) => i.id}
          searchable
          exportable
          exportFilename="institutes"
          searchKeys={[(i) => i.name, (i) => i.code, (i) => i.description ?? '']}
          onRowContextMenu={getContextItems}
        />
      </Card>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title={editing ? 'Edit Institute' : 'Add Institute'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button>
            <Button loading={saving} onClick={handleSave}>Save</Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Main Campus" />
          <Input label="Code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} placeholder="MAIN" helpText="Short identifier (e.g. MAIN, ENG, SCI)" />
          <Input label="Description" value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="Optional description" />
        </div>
      </Modal>

      <ConfirmDialog
        open={confirmId !== null}
        title="Delete Institute"
        message="This will fail if the institute still has departments. Move or delete its departments first."
        confirmLabel="Delete"
        variant="danger"
        loading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmId(null)}
      />
    </>
  )
}