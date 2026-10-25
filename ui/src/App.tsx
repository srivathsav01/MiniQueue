import './App.css'
import { Toaster } from "@/components/ui/sonner"
import { ConfirmDialogProvider } from './context/ConfirmDialogContext'
import { RefreshProvider } from './context/RefreshContext'
import LiveActivity from './components/LiveActivity'
import AppLayout from './page/AppLayout'
import OverviewPage from './page/OverviewPage'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { MonitorWebSocketProvider } from './context/MonitorWebSocketContext'
import ManagePage from './page/ManagePage'
import ErrorDisplay from './page/ErrorPage'
import { AuthProvider } from './context/AuthContext'
import RequireAuth, { RequireAdmin } from './auth/RequireAuth'
import LoginPage from './page/LoginPage'
import LoginCallbackPage from './page/LoginCallbackPage'
import ClientsPage from './page/ClientsPage'

// Created once, outside the component: rebuilding the router on every render of App would
// reset navigation state.
const router = createBrowserRouter([
  // Public: the sign-in page and the page GitHub sign-in returns to.
  { path: '/login', element: <LoginPage /> },
  { path: '/login/callback', element: <LoginCallbackPage /> },
  {
    // Everything else needs a session. The live-feed socket lives inside the guard, so it only
    // connects once someone is signed in, and disconnects when they sign out.
    element: (
      <RequireAuth>
        <MonitorWebSocketProvider>
          <AppLayout />
        </MonitorWebSocketProvider>
      </RequireAuth>
    ),
    children: [
      { path: '/', element: <OverviewPage /> },
      { path: '/home', element: <OverviewPage /> },
      { path: '/live', element: <LiveActivity /> },
      { path: '/manage', element: <ManagePage /> },
      { path: '/clients', element: <RequireAdmin><ClientsPage /></RequireAdmin> },
    ],
    errorElement: <ErrorDisplay message={"404 Page Not Found"} variant="notFound" />
  }
]);

function App() {
  return (
    <>
    <ConfirmDialogProvider>
      <RefreshProvider>
        <AuthProvider>
          <RouterProvider router={router} />
          <Toaster />
        </AuthProvider>
      </RefreshProvider>
    </ConfirmDialogProvider>
    </>
  )
}

export default App