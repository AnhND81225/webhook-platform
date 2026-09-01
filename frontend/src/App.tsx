import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AuthenticatedLayout } from './layouts/AuthenticatedLayout'
import { AppIndexPage } from './pages/AppIndexPage'
import { ApplicationsPage } from './pages/ApplicationsPage'
import { ApplicationSettingsPage } from './pages/ApplicationSettingsPage'
import { DeliveryDetailPage } from './pages/DeliveryDetailPage'
import { DeliveriesPage } from './pages/DeliveriesPage'
import { EventDetailPage } from './pages/EventDetailPage'
import { EventsPage } from './pages/EventsPage'
import { LoginPage } from './pages/LoginPage'
import { LandingPage } from './pages/LandingPage'
import { OverviewPage } from './pages/OverviewPage'
import { ApiKeysPage } from './pages/ApiKeysPage'
import { EndpointsPage } from './pages/EndpointsPage'
import { EndpointDetailPage } from './pages/EndpointDetailPage'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
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
        <Route path="applications" element={<ApplicationsPage />} />
        <Route path=":applicationId/endpoints" element={<EndpointsPage />} />
        <Route path=":applicationId/endpoints/:endpointId" element={<EndpointDetailPage />} />
        <Route path=":applicationId/api-keys" element={<ApiKeysPage />} />
        <Route path=":applicationId" element={<OverviewPage />} />
        <Route path=":applicationId/settings" element={<ApplicationSettingsPage />} />
        <Route path=":applicationId/events" element={<EventsPage />} />
        <Route path=":applicationId/events/:eventId" element={<EventDetailPage />} />
        <Route path=":applicationId/deliveries" element={<DeliveriesPage />} />
        <Route path=":applicationId/deliveries/:deliveryId" element={<DeliveryDetailPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes>
  )
}
