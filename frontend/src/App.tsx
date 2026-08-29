import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthenticatedLayout } from './layouts/AuthenticatedLayout'
import { FoundationPage } from './pages/FoundationPage'
import { LoginPage } from './pages/LoginPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route path="/app" element={<AuthenticatedLayout />}>
        <Route index element={<FoundationPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

