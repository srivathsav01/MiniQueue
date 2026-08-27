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


function App() {
  const router = createBrowserRouter([
    {
      element: <AppLayout />,
      children: [
        { path: '/', element: <OverviewPage /> },
        { path: '/home', element: <OverviewPage /> },
        { path: '/live', element: <LiveActivity /> },
        { path: '/manage', element: <ManagePage /> },
      ]
    }
  ]);
  return (
    <>
    <ConfirmDialogProvider>
      <RefreshProvider>
        <MonitorWebSocketProvider>
          <RouterProvider router={router} />
          <Toaster />
        </MonitorWebSocketProvider>
        </RefreshProvider>
      </ConfirmDialogProvider>
    </>
  )
}

export default App
