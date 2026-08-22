import { useCallback, useEffect, useRef, useState } from 'react'
import { solveJobApi } from '../services/api'
import { isSolveJobTerminal } from '../types'
import type { SolveJobResponse } from '../types'

export interface SolveJobState {
  job: SolveJobResponse
  done: boolean
  error?: string
  cancel: () => Promise<void>
}

const POLL_INTERVAL_MS = 2000
const MAX_CONSECUTIVE_FAILURES = 5

function alreadyDone(job: SolveJobResponse): boolean {
  return job.id == null || isSolveJobTerminal(job)
}

/**
 * Polls a solve job until it reaches a terminal state (or was returned with
 * no id, which means the backend applied the change synchronously and there
 * is nothing to poll — see the "no-op" completed response).
 */
export function useSolveJobPoll(initial: SolveJobResponse, onDone?: (job: SolveJobResponse) => void): SolveJobState {
  const [job, setJob] = useState<SolveJobResponse>(initial)
  const [done, setDone] = useState<boolean>(() => alreadyDone(initial))
  const [error, setError] = useState<string | undefined>(undefined)
  const onDoneRef = useRef(onDone)
  onDoneRef.current = onDone
  const stoppedRef = useRef(false)

  const stop = useCallback(() => {
    stoppedRef.current = true
  }, [])

  useEffect(() => {
    if (alreadyDone(job)) return
    stoppedRef.current = false
    let failures = 0
    const timer = window.setInterval(async () => {
      if (stoppedRef.current) return
      try {
        const fresh = await solveJobApi.getById(job.id as number)
        if (stoppedRef.current) return
        failures = 0
        setJob(fresh)
        if (isSolveJobTerminal(fresh)) {
          stop()
          window.clearInterval(timer)
          setDone(true)
          onDoneRef.current?.(fresh)
        }
      } catch {
        if (stoppedRef.current) return
        failures += 1
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
          stop()
          window.clearInterval(timer)
          setError('Solver progress is unreachable — the backend may be down. Refresh to retry.')
          setDone(true)
        }
      }
    }, POLL_INTERVAL_MS)
    return () => {
      stoppedRef.current = true
      window.clearInterval(timer)
    }
  }, [job.id, stop])

  const cancel = useCallback(async () => {
    if (job.id == null || isSolveJobTerminal(job) || stoppedRef.current) return
    stop()
    try {
      await solveJobApi.cancel(job.id)
      setJob((prev) => ({ ...prev, status: 'CANCELLED' }))
      setDone(true)
    } catch {
      // Cancel is best-effort; a terminal response is already settled either way.
    }
  }, [job, stop])

  return { job, done, error, cancel }
}

const WAIT_TIMEOUT_MS = 20 * 60 * 1000

/**
 * Resolves when the given job reaches a terminal state. Jobs returned with
 * no id (synchronous no-op responses) resolve immediately. Rejects after a
 * generous timeout so callers can never hang forever on a wedged backend.
 */
export async function waitForJob(job: SolveJobResponse, timeoutMs: number = WAIT_TIMEOUT_MS): Promise<SolveJobResponse> {
  if (alreadyDone(job)) return job
  const deadline = Date.now() + timeoutMs
  for (;;) {
    if (Date.now() > deadline) {
      throw new Error('The solve job did not finish in time. Check the backend and try again.')
    }
    await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
    const fresh = await solveJobApi.getById(job.id as number)
    if (isSolveJobTerminal(fresh)) return fresh
  }
}