import {
  createContext,
  useCallback,
  useContext,
  useState,
  type ReactNode,
} from "react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";


interface ConfirmDialogOptions {
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "default" | "destructive";
  onConfirm: () => void;
  onCancel?: () => void;
}

interface ConfirmDialogContextValue {
  openConfirmDialog: (options: ConfirmDialogOptions) => void;
}


const ConfirmDialogContext = createContext<ConfirmDialogContextValue | null>(null);


function ConfirmDialogProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const [options, setOptions] = useState<ConfirmDialogOptions | null>(null);

  const openConfirmDialog = useCallback((opts: ConfirmDialogOptions) => {
    setOptions(opts);
    setOpen(true);
  }, []);

  const handleConfirm = () => {
    options?.onConfirm();
    setOpen(false);
  };

  const handleCancel = () => {
    options?.onCancel?.();
    setOpen(false);
  };

  const isDestructive = options?.variant === "destructive";

  return (
    <ConfirmDialogContext.Provider value={{ openConfirmDialog }}>
      {children}

      <AlertDialog open={open} onOpenChange={(val) => !val && handleCancel()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{options?.title}</AlertDialogTitle>
            <AlertDialogDescription>{options?.description}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={handleCancel}>
              {options?.cancelLabel ?? "Cancel"}
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleConfirm}
              className={
                isDestructive
                  ? "bg-rose-600 hover:bg-rose-700 text-white"
                  : ""
              }
            >
              {options?.confirmLabel ?? "Confirm"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </ConfirmDialogContext.Provider>
  );
}


function useConfirmDialog(): ConfirmDialogContextValue {
  const ctx = useContext(ConfirmDialogContext);
  if (!ctx) {
    throw new Error("useConfirmDialog must be used inside <ConfirmDialogProvider>");
  }
  return ctx;
}

export {useConfirmDialog, ConfirmDialogProvider};