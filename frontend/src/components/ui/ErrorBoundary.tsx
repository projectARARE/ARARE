import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { AlertTriangle, RefreshCw } from 'lucide-react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * Catches render/lifecycle crashes anywhere under this boundary and shows a
 * recoverable fallback instead of a blank white screen. The reset button
 * re-mounts the tree, which is enough for transient state/race failures.
 */
export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error('Unhandled UI error:', error, info.componentStack)
  }

  private reset = (): void => {
    this.setState({ error: null })
  }

  render(): ReactNode {
    if (this.state.error !== null) {
      return (
        <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 px-4 text-center">
          <div className="flex items-center gap-2 text-rose-600">
            <AlertTriangle size={28} />
            <h1 className="text-lg font-semibold text-slate-900">Something went wrong</h1>
          </div>
          <p className="max-w-md text-sm text-slate-500">
            The page hit an unexpected error. Your data is safe — reload the page or
            go back and try again.
          </p>
          <pre className="max-w-lg overflow-auto rounded-lg bg-slate-50 border border-slate-200 px-4 py-3 text-xs text-slate-600">
            {this.state.error.message}
          </pre>
          <button
            type="button"
            onClick={this.reset}
            className="flex items-center gap-1.5 rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            <RefreshCw size={14} />
            Reload page
          </button>
        </div>
      )
    }
    return this.props.children
  }
}