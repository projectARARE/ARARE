import { useEffect, useCallback, useRef } from 'react'
import { useBlocker } from 'react-router-dom'

/**
 * Warns the user before navigating away when there are unsaved changes.
 * @param isDirty - true when the form has unsaved changes
 */
export function useUnsavedChanges(isDirty: boolean) {
  const isDirtyRef = useRef(isDirty)

  useEffect(() => {
    isDirtyRef.current = isDirty
  }, [isDirty])

  // Block react-router navigation
  useBlocker(
    useCallback(
      () => isDirtyRef.current
        ? !window.confirm('You have unsaved changes. Leave anyway?')
        : false,
      [],
    ),
  )

  // Block browser close / reload
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (isDirtyRef.current) {
        e.preventDefault()
        e.returnValue = ''
      }
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [])
}
