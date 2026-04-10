import { lazy, Suspense } from 'react'
import type { ReactNode } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/layout/Layout'
import { ToastProvider } from './contexts/ToastContext'
import ToastContainer from './components/ui/Toast'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const Buildings = lazy(() => import('./pages/Buildings'))
const Rooms = lazy(() => import('./pages/Rooms'))
const Teachers = lazy(() => import('./pages/Teachers'))
const Subjects = lazy(() => import('./pages/Subjects'))
const Departments = lazy(() => import('./pages/Departments'))
const Batches = lazy(() => import('./pages/Batches'))
const ClassSections = lazy(() => import('./pages/ClassSections'))
const Timeslots = lazy(() => import('./pages/Timeslots'))
const UniversityConfigPage = lazy(() => import('./pages/UniversityConfig'))
const ScheduleGenerator = lazy(() => import('./pages/ScheduleGenerator'))
const TimetableViewer = lazy(() => import('./pages/TimetableViewer'))
const ScheduleHistory = lazy(() => import('./pages/ScheduleHistory'))
const Events = lazy(() => import('./pages/Events'))
const AcademicTerms = lazy(() => import('./pages/AcademicTerms'))
const DisruptionHandling = lazy(() => import('./pages/DisruptionHandling'))
const AnalyticsDashboard = lazy(() => import('./pages/AnalyticsDashboard'))
const WhatIfComparison = lazy(() => import('./pages/WhatIfComparison'))
const CalendarPortal = lazy(() => import('./pages/CalendarPortal'))
const ConstraintConfig = lazy(() => import('./pages/ConstraintConfig'))
const CsvImport = lazy(() => import('./pages/CsvImport'))

function page(element: ReactNode) {
  return (
    <Suspense fallback={<div className="animate-pulse h-64 rounded-lg bg-slate-100" />}>
      {element}
    </Suspense>
  )
}

export default function App() {
  return (
    <ToastProvider>
      <ToastContainer />
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={page(<Dashboard />)} />
          <Route path="buildings" element={page(<Buildings />)} />
          <Route path="rooms" element={page(<Rooms />)} />
          <Route path="teachers" element={page(<Teachers />)} />
          <Route path="subjects" element={page(<Subjects />)} />
          <Route path="departments" element={page(<Departments />)} />
          <Route path="batches" element={page(<Batches />)} />
          <Route path="sections" element={page(<ClassSections />)} />
          <Route path="timeslots" element={page(<Timeslots />)} />
          <Route path="config" element={page(<UniversityConfigPage />)} />
          <Route path="schedule/generate" element={page(<ScheduleGenerator />)} />
          <Route path="schedule/view/:id" element={page(<TimetableViewer />)} />
          <Route path="schedule/history" element={page(<ScheduleHistory />)} />
          <Route path="events" element={page(<Events />)} />
          <Route path="disruptions" element={page(<DisruptionHandling />)} />
          <Route path="academic-terms" element={page(<AcademicTerms />)} />
          <Route path="analytics" element={page(<AnalyticsDashboard />)} />
          <Route path="what-if" element={page(<WhatIfComparison />)} />
          <Route path="portal" element={page(<CalendarPortal />)} />
          <Route path="constraints" element={page(<ConstraintConfig />)} />
          <Route path="import/csv" element={page(<CsvImport />)} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Routes>
    </ToastProvider>
  )
}
