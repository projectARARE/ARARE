import { useCallback, useEffect, useRef, useState } from 'react'
import { solveJobApi } from '../services/api'
import { isSolveJobTerminal } from '../types'
import type { SolveJobResponse } from '../types'

export interface SolveJobState {
  job: SolveJobResponse
  done: boolean
  cancel: () => Promise<void>
}

const POLL_INTERVAL_MS = 2000

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
  const onDoneRef = useRef(onDone)
  onDoneRef.current = onDone

  useEffect(() => {
    if (alreadyDone(job)) return
    const timer = window.setInterval(async () => {
      try {
        const fresh = await solveJobApi.getById(job.id as number)
        setJob(fresh)
        if (isSolveJobTerminal(fresh)) {
          setDone(true)
          onDoneRef.current?.(fresh)
        }
      } catch {
        // Transient poll failure: keep polling until the browser gives up.
      }
    }, POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [job.id])

  const cancel = useCallback(async () => {
    if (job.id == null || isSolveJobTerminal(job)) return
    try {
      await solveJobApi.cancel(job.id)
      setJob((prev) => ({ ...prev, status: 'CANCELLED' }))
      setDone(true)
    } catch {
      // Cancel is best-effort; the next poll will reflect the true state.
    }
  }, [job])

  return { job, done, cancel }
}

/**
 * Resolves when the given job reaches a terminal state. Jobs returned with
 * no id (synchronous no-op responses) resolve immediately.
 */
export async function waitForJob(job: SolveJobResponse): Promise<SolveJobResponse> {
  if (alreadyDone(job)) return job
  for (;;) {
    await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS))
    const fresh = await solveJobApi.getById(job.id as number)
    if (isSolveJobTerminal(fresh)) return fresh
  }
}
