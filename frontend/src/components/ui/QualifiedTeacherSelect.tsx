import { useEffect, useState } from 'react'
import SearchableSelect from './SearchableSelect'
import type { Teacher } from '../../types'

export function isTeacherQualifiedForSubject(teacher: Teacher, subjectId?: number | null): boolean {
  return subjectId != null && (teacher.subjectIds ?? []).includes(subjectId)
}

interface QualifiedTeacherSelectProps {
  teachers: Teacher[]
  /** When set, options default to only teachers qualified for this subject. */
  subjectId?: number | null
  value: number | null
  onChange: (value: number | null) => void
  label?: string
  placeholder?: string
  allowClear?: boolean
  disabled?: boolean
  className?: string
}

/**
 * Teacher picker that defaults to ONLY teachers qualified for the currently
 * selected subject (the enterprise pattern — no "this teacher is not
 * qualified" surprises after the fact). When no qualified teacher exists it
 * states that clearly and offers a "show unqualified anyway" escape hatch;
 * the currently assigned teacher is always listed (marked) so edits never
 * orphan a value. Without a subjectId every teacher is offered.
 */
export default function QualifiedTeacherSelect({
  teachers,
  subjectId,
  value,
  onChange,
  label = 'Teacher',
  placeholder = 'Select teacher…',
  allowClear = true,
  disabled = false,
  className,
}: QualifiedTeacherSelectProps) {
  const [showUnqualified, setShowUnqualified] = useState(false)

  const hasSubject = subjectId != null && subjectId > 0
  const qualified = hasSubject
    ? teachers.filter((t) => isTeacherQualifiedForSubject(t, subjectId))
    : teachers
  const unqualified = hasSubject
    ? teachers.filter((t) => !isTeacherQualifiedForSubject(t, subjectId))
    : []
  const currentIsUnqualified =
    value != null && unqualified.some((t) => t.id === value)

  useEffect(() => {
    setShowUnqualified(false)
  }, [subjectId])

  const options = [
    ...qualified.map((t) => ({ value: t.id, label: t.name })),
    ...(showUnqualified || currentIsUnqualified
      ? unqualified.map((t) => ({
          value: t.id,
          label: currentIsUnqualified && t.id === value
            ? `${t.name} · currently assigned (not qualified)`
            : `${t.name} · not qualified`,
        }))
      : []),
  ]

  const noneQualified = hasSubject && qualified.length === 0

  return (
    <div className={className}>
      <SearchableSelect
        label={label}
        value={value}
        onChange={(v) => onChange(typeof v === 'number' ? v : v == null ? null : Number(v))}
        options={options}
        placeholder={placeholder}
        allowClear={allowClear}
        disabled={disabled}
      />
      {hasSubject && !disabled && (unqualified.length > 0 || noneQualified) && (
        <div className="mt-1.5 flex items-center justify-between gap-2">
          <span className={`text-xs ${noneQualified ? 'text-amber-700' : 'text-gray-500'}`}>
            {noneQualified
              ? 'No qualified teacher for this subject.'
              : `${qualified.length} qualified teacher${qualified.length === 1 ? '' : 's'}`}
          </span>
          <button
            type="button"
            onClick={() => setShowUnqualified((s) => !s)}
            className="text-xs font-medium text-indigo-600 hover:text-indigo-700 focus:outline-none focus:underline"
          >
            {showUnqualified ? 'Hide unqualified' : 'Show unqualified teachers'}
          </button>
        </div>
      )}
    </div>
  )
}