import { useEffect, useMemo, useState } from 'react'
import { Card, Select, Button } from '../components/ui'
import { batchApi, teacherApi, scheduleApi } from '../services/api'
import type { Batch, Teacher } from '../types'
import { useToast } from '../contexts/ToastContext'

export default function CalendarPortal() {
  const { toast } = useToast()
  const [teachers, setTeachers] = useState<Teacher[]>([])
  const [batches, setBatches] = useState<Batch[]>([])
  const [selectedTeacherId, setSelectedTeacherId] = useState<number | undefined>()
  const [selectedBatchId, setSelectedBatchId] = useState<number | undefined>()

  useEffect(() => {
    Promise.allSettled([teacherApi.getAll(), batchApi.getAll()])
      .then(([t, b]) => {
        if (t.status === 'fulfilled') setTeachers(t.value)
        if (b.status === 'fulfilled') setBatches(b.value)
      })
  }, [])

  const teacherLink = useMemo(() =>
    selectedTeacherId ? `${window.location.origin}${scheduleApi.getTeacherIcalUrl(selectedTeacherId)}` : '',
  [selectedTeacherId])
  const batchLink = useMemo(() =>
    selectedBatchId ? `${window.location.origin}${scheduleApi.getBatchIcalUrl(selectedBatchId)}` : '',
  [selectedBatchId])

  const copy = async (text: string) => {
    if (!text) return
    try {
      await navigator.clipboard.writeText(text)
    } catch {
    }
  }

  const saveBlob = (blob: Blob, fileName: string) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const downloadTeacher = async () => {
    if (!selectedTeacherId) return
    try {
      const blob = await scheduleApi.downloadTeacherIcal(selectedTeacherId)
      saveBlob(blob, `teacher-${selectedTeacherId}.ics`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Download failed')
    }
  }

  const downloadBatch = async () => {
    if (!selectedBatchId) return
    try {
      const blob = await scheduleApi.downloadBatchIcal(selectedBatchId)
      saveBlob(blob, `batch-${selectedBatchId}.ics`)
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Download failed')
    }
  }

  return (
    <div className="space-y-4">
      <Card title="Calendar Subscription Portal" description="Generate iCal URLs for teachers and batches.">
        <div className="grid md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Select
              label="Teacher"
              value={selectedTeacherId ?? ''}
              onChange={(e) => setSelectedTeacherId(+e.target.value)}
              options={teachers.map((t) => ({ value: t.id, label: t.name }))}
            />
            <input className="input" readOnly value={teacherLink} placeholder="Teacher iCal URL" />
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => copy(teacherLink)}>Copy Teacher URL</Button>
              <Button variant="secondary" onClick={downloadTeacher}>Download .ics</Button>
            </div>
          </div>

          <div className="space-y-2">
            <Select
              label="Batch"
              value={selectedBatchId ?? ''}
              onChange={(e) => setSelectedBatchId(+e.target.value)}
              options={batches.map((b) => ({ value: b.id, label: `Yr ${b.year}-${b.section}` }))}
            />
            <input className="input" readOnly value={batchLink} placeholder="Batch iCal URL" />
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => copy(batchLink)}>Copy Batch URL</Button>
              <Button variant="secondary" onClick={downloadBatch}>Download .ics</Button>
            </div>
          </div>
        </div>
      </Card>

      <Card title="How to Subscribe" description="Works with Google Calendar, Apple Calendar, and Outlook.">
        <ol className="list-decimal ml-5 text-sm text-slate-700 space-y-1">
          <li>Copy the generated iCal URL for a teacher or batch.</li>
          <li>Open your calendar app and add a calendar by URL.</li>
          <li>Paste the link and save. Future schedule updates will reflect on sync.</li>
        </ol>
      </Card>
    </div>
  )
}
