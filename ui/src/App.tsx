import './App.css'
import Overview from './components/Overview'
import QueueStats from './components/QueueStats'
import DeadLetterQueueStats from './components/DeadLetterQueue'
import { Toaster } from "@/components/ui/sonner"
import { ConfirmDialogProvider } from './context/ConfirmDialogContext'
import { RefreshProvider } from './context/RefreshContext'

function App() {

  return (
    <>
    <ConfirmDialogProvider>
      <RefreshProvider>
        <Overview />
        <QueueStats />
        <DeadLetterQueueStats />
        <Toaster />
        </RefreshProvider>
      </ConfirmDialogProvider>
    </>
  )
}

export default App
