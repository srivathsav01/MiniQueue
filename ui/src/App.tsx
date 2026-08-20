import './App.css'
import { Toaster } from "@/components/ui/sonner"
import { ConfirmDialogProvider } from './context/ConfirmDialogContext'
import { RefreshProvider } from './context/RefreshContext'
import LiveActivity from './components/LiveActivity'
import AppLayout from './page/AppLayout'
import OverviewPage from './page/OverviewPage'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'


function App() {
  const router = createBrowserRouter([
    {
      element: <AppLayout />,
      children: [
        { path: '/', element: <OverviewPage /> },
        { path: '/home', element: <OverviewPage /> },
        { path: '/live', element: <LiveActivity /> },
      ]
    }
  ]);
  return (
    <>
    <ConfirmDialogProvider>
      <RefreshProvider>
        <RouterProvider router={router} />
        <Toaster />
        </RefreshProvider>
      </ConfirmDialogProvider>
    </>
  )
}

export default App
