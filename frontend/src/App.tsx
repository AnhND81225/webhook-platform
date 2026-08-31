import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AuthenticatedLayout } from './layouts/AuthenticatedLayout'
import { AppIndexPage } from './pages/AppIndexPage'
import { DeliveryDetailPage } from './pages/DeliveryDetailPage'
import { DeliveriesPage } from './pages/DeliveriesPage'
import { EventDetailPage } from './pages/EventDetailPage'
import { EventsPage } from './pages/EventsPage'
import { LoginPage } from './pages/LoginPage'
import { OverviewPage } from './pages/OverviewPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/app" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/app"
        element={(
          <ProtectedRoute>
            <AuthenticatedLayout />
          </ProtectedRoute>
        )}
      >
        <Route index element={<AppIndexPage />} />
        <Route path=":applicationId" element={<OverviewPage />} />
        <Route path=":applicationId/events" element={<EventsPage />} />
        <Route path=":applicationId/events/:eventId" element={<EventDetailPage />} />
        <Route path=":applicationId/deliveries" element={<DeliveriesPage />} />
        <Route path=":applicationId/deliveries/:deliveryId" element={<DeliveryDetailPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  )
}
