import { useMemo, useState } from 'react'
import { Button, Card, Select } from '../components/ui'
import { importApi } from '../services/api'
import type { CsvImportResponse } from '../types'

type EntityType =
  | 'timeslots'
  | 'buildings'
  | 'departments'
  | 'rooms'
  | 'subjects'
  | 'teachers'
  | 'batches'

const ENTITY_OPTIONS: { value: EntityType; label: string }[] = [
  { value: 'timeslots', label: 'Timeslots' },
  { value: 'buildings', label: 'Buildings' },
  { value: 'departments', label: 'Departments' },
  { value: 'rooms', label: 'Rooms' },
  { value: 'subjects', label: 'Subjects' },
  { value: 'teachers', label: 'Teachers' },
  { value: 'batches', label: 'Batches' },
]

const CSV_TEMPLATES: Record<EntityType, string> = {
  timeslots: [
    'day,startTime,endTime,slotNumber,type',
    'MONDAY,09:00,10:00,1,CLASS',
    'MONDAY,10:00,11:00,2,CLASS',
  ].join('\n'),
  buildings: [
    'name,location',
    'Block A,North Campus',
    'Block B,Main Campus',
  ].join('\n'),
  departments: [
    'name,code,buildingNames',
    'Computer Science,CSE,Block A;Block B',
    'Electronics,ECE,Block B',
  ].join('\n'),
  rooms: [
    'buildingName,roomNumber,type,labSubtype,capacity,availableTimeslots',
    'Block A,LAB-101,LAB,COMPUTER_LAB,60,MONDAY@09:00-10:00;MONDAY@10:00-11:00',
    'Block B,201,LECTURE,,120,',
  ].join('\n'),
  subjects: [
    'name,code,departmentCode,weeklyHours,chunkHours,roomTypeRequired,labSubtypeRequired,isLab,requiresTeacher,requiresRoom,minGapBetweenSessions,maxSessionsPerDay',
    'Data Structures,CSE201,CSE,4,1,LECTURE,,false,true,true,0,1',
    'DS Lab,CSE251,CSE,2,2,LAB,COMPUTER_LAB,true,true,true,0,1',
  ].join('\n'),
  teachers: [
    'name,subjectCodes,availableTimeslots,preferredBuildingNames,maxDailyHours,maxWeeklyHours,maxConsecutiveClasses,movementPenalty,preferredFreeDay',
    'Dr. Sharma,CSE:CSE201|CSE:CSE251,MONDAY@09:00-10:00;MONDAY@10:00-11:00,Block A,6,20,3,1,FRIDAY',
  ].join('\n'),
  batches: [
    'departmentCode,year,section,studentCount,workingDays,preferredFreeDay',
    'CSE,2,A,72,MONDAY;TUESDAY;WEDNESDAY;THURSDAY;FRIDAY,SATURDAY',
  ].join('\n'),
}

const CSV_EXAMPLES: Record<EntityType, string> = {
  timeslots: [
    'day,startTime,endTime,slotNumber,type',
    'MONDAY,09:00,10:00,1,CLASS',
    'MONDAY,10:00,11:00,2,CLASS',
    'MONDAY,11:00,12:00,3,CLASS',
    'MONDAY,12:00,13:00,,BREAK',
    'TUESDAY,09:00,10:00,1,CLASS',
    'TUESDAY,10:00,11:00,2,CLASS',
    'WEDNESDAY,09:00,10:00,1,CLASS',
    'THURSDAY,09:00,10:00,1,CLASS',
    'FRIDAY,09:00,10:00,1,CLASS',
  ].join('\n'),
  buildings: [
    'name,location',
    'Block A,North Campus',
    'Block B,Main Campus',
    'Lab Complex,Innovation Wing',
  ].join('\n'),
  departments: [
    'name,code,buildingNames',
    'Computer Science,CSE,Block A;Lab Complex',
    'Electronics,ECE,Block B;Lab Complex',
  ].join('\n'),
  rooms: [
    'buildingName,roomNumber,type,labSubtype,capacity,availableTimeslots',
    'Block A,201,LECTURE,,120,',
    'Block A,202,LECTURE,,90,',
    'Lab Complex,LAB-101,LAB,COMPUTER_LAB,60,MONDAY@09:00-10:00;MONDAY@10:00-11:00;TUESDAY@09:00-10:00',
    'Lab Complex,LAB-201,LAB,ELECTRONICS_LAB,48,TUESDAY@10:00-11:00;WEDNESDAY@09:00-10:00',
  ].join('\n'),
  subjects: [
    'name,code,departmentCode,weeklyHours,chunkHours,roomTypeRequired,labSubtypeRequired,isLab,requiresTeacher,requiresRoom,minGapBetweenSessions,maxSessionsPerDay',
    'Data Structures,CSE201,CSE,4,1,LECTURE,,false,true,true,0,1',
    'Database Systems,CSE301,CSE,3,1,LECTURE,,false,true,true,0,1',
    'DS Lab,CSE251,CSE,2,2,LAB,COMPUTER_LAB,true,true,true,0,1',
    'Circuits,ECE201,ECE,4,1,LECTURE,,false,true,true,0,1',
    'Circuit Lab,ECE251,ECE,2,2,LAB,ELECTRONICS_LAB,true,true,true,0,1',
  ].join('\n'),
  teachers: [
    'name,subjectCodes,availableTimeslots,preferredBuildingNames,maxDailyHours,maxWeeklyHours,maxConsecutiveClasses,movementPenalty,preferredFreeDay',
    'Dr. Sharma,CSE:CSE201|CSE:CSE251,MONDAY@09:00-10:00;MONDAY@10:00-11:00;TUESDAY@09:00-10:00,Block A;Lab Complex,6,20,3,1,FRIDAY',
    'Prof. Iyer,CSE:CSE301,MONDAY@10:00-11:00;WEDNESDAY@09:00-10:00;THURSDAY@09:00-10:00,Block A,5,18,3,1,SATURDAY',
    'Dr. Menon,ECE:ECE201|ECE:ECE251,TUESDAY@10:00-11:00;WEDNESDAY@09:00-10:00;FRIDAY@09:00-10:00,Block B;Lab Complex,6,20,3,2,MONDAY',
  ].join('\n'),
  batches: [
    'departmentCode,year,section,studentCount,workingDays,preferredFreeDay',
    'CSE,2,A,72,MONDAY;TUESDAY;WEDNESDAY;THURSDAY;FRIDAY,SATURDAY',
    'CSE,2,B,68,MONDAY;TUESDAY;WEDNESDAY;THURSDAY;FRIDAY,SATURDAY',
    'ECE,2,A,64,MONDAY;TUESDAY;WEDNESDAY;THURSDAY;FRIDAY,SATURDAY',
  ].join('\n'),
}

export default function CsvImport() {
  const [entityType, setEntityType] = useState<EntityType>('timeslots')
  const [csvContent, setCsvContent] = useState(CSV_TEMPLATES.timeslots)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<CsvImportResponse | null>(null)

  const currentTemplate = useMemo(() => CSV_TEMPLATES[entityType], [entityType])

  const loadTemplate = () => {
    setCsvContent(currentTemplate)
  }

  const clearInput = () => {
    setCsvContent('')
    setResult(null)
    setError(null)
  }

  const downloadTemplate = (type: EntityType) => {
    const content = CSV_TEMPLATES[type]
    const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${type}-template.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }

  const downloadCurrentTemplate = () => {
    downloadTemplate(entityType)
  }

  const downloadAllTemplates = () => {
    ENTITY_OPTIONS.forEach((opt) => downloadTemplate(opt.value))
  }

  const downloadExampleDataPack = () => {
    ENTITY_OPTIONS.forEach((opt) => {
      const content = CSV_EXAMPLES[opt.value]
      const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${opt.value}-example.csv`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    })
  }

  const handleImport = async () => {
    if (!csvContent.trim()) {
      setError('CSV content is empty.')
      return
    }

    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const response = await importApi.importCsv(entityType, csvContent)
      setResult(response)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Import failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <Card title="CSV Bulk Import" description="Paste CSV data and import directly into backend entities.">
        <div className="grid md:grid-cols-[280px_1fr] gap-4">
          <div className="space-y-3">
            <Select
              label="Entity"
              value={entityType}
              onChange={(e) => setEntityType(e.target.value as EntityType)}
              options={ENTITY_OPTIONS}
            />
            <div className="space-y-2">
              <Button variant="secondary" onClick={loadTemplate}>Load Template</Button>
              <Button variant="secondary" onClick={downloadCurrentTemplate}>Download Current Template</Button>
              <Button variant="secondary" onClick={downloadAllTemplates}>Download All Templates</Button>
              <Button variant="secondary" onClick={downloadExampleDataPack}>Download Example Data Pack</Button>
              <Button variant="ghost" onClick={clearInput}>Clear</Button>
            </div>
            <p className="text-xs text-gray-500">
              Multi-value fields use ; or | separators. Timeslot token format: DAY@HH:mm-HH:mm.
            </p>
            <p className="text-xs text-gray-500">
              Recommended import order: timeslots, then buildings, departments, rooms, subjects, teachers, and batches.
            </p>
          </div>

          <div className="space-y-2">
            <label className="form-label" htmlFor="csv-content">CSV Content</label>
            <textarea
              id="csv-content"
              className="block w-full min-h-[320px] rounded-md border border-gray-300 px-3 py-2 text-sm font-mono shadow-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-primary-500"
              value={csvContent}
              onChange={(e) => setCsvContent(e.target.value)}
              placeholder="Paste CSV here"
            />
            <div className="flex justify-end">
              <Button loading={loading} onClick={handleImport}>Run Import</Button>
            </div>
          </div>
        </div>
      </Card>

      {error && (
        <Card title="Import Error">
          <p className="text-sm text-red-700">{error}</p>
        </Card>
      )}

      {result && (
        <Card title="Import Result">
          <div className="grid md:grid-cols-4 gap-3 text-sm">
            <div className="rounded border border-gray-200 px-3 py-2">Entity: <strong>{result.entityType}</strong></div>
            <div className="rounded border border-gray-200 px-3 py-2">Created: <strong>{result.created}</strong></div>
            <div className="rounded border border-gray-200 px-3 py-2">Updated: <strong>{result.updated}</strong></div>
            <div className="rounded border border-gray-200 px-3 py-2">Skipped: <strong>{result.skipped}</strong></div>
          </div>

          {result.errors.length > 0 && (
            <div className="mt-4">
              <p className="text-sm font-medium text-gray-800 mb-2">Row Errors</p>
              <div className="max-h-64 overflow-y-auto rounded border border-red-200 bg-red-50">
                {result.errors.map((line) => (
                  <div key={line} className="border-b border-red-100 last:border-b-0 px-3 py-2 text-xs text-red-800">
                    {line}
                  </div>
                ))}
              </div>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
