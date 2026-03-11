import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/layout/Layout'
import Dashboard from './pages/Dashboard'
import Buildings from './pages/Buildings'
import Rooms from './pages/Rooms'
import Teachers from './pages/Teachers'
import Subjects from './pages/Subjects'
import Departments from './pages/Departments'
import Batches from './pages/Batches'
import ClassSections from './pages/ClassSections'
import Timeslots from './pages/Timeslots'
import UniversityConfigPage from './pages/UniversityConfig'
import ScheduleGenerator from './pages/ScheduleGenerator'
import TimetableViewer from './pages/TimetableViewer'
import ScheduleHistory from './pages/ScheduleHistory'
import Events from './pages/Events'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="buildings" element={<Buildings />} />
        <Route path="rooms" element={<Rooms />} />
        <Route path="teachers" element={<Teachers />} />
        <Route path="subjects" element={<Subjects />} />
        <Route path="departments" element={<Departments />} />
        <Route path="batches" element={<Batches />} />
        <Route path="sections" element={<ClassSections />} />
        <Route path="timeslots" element={<Timeslots />} />
        <Route path="config" element={<UniversityConfigPage />} />
        <Route path="schedule/generate" element={<ScheduleGenerator />} />
        <Route path="schedule/view/:id" element={<TimetableViewer />} />
        <Route path="schedule/history" element={<ScheduleHistory />} />
        <Route path="events" element={<Events />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
    </Routes>
  )
}
