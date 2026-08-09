import './App.css'
import Overview from './components/Overview'
import QueueStats from './components/QueueStats'
import DeadLetterQueueStats from './components/DeadLetterQueue'
import { Toaster } from "@/components/ui/sonner"
import { ConfirmDialogProvider } from './context/ConfirmDialogContext'

function App() {

  return (
    <>
    <ConfirmDialogProvider>
        <Overview />
        <QueueStats />
        <DeadLetterQueueStats />
        <Toaster />
      </ConfirmDialogProvider>
    </>
  )
}

export default App
