import {
  createContext,
  useCallback,
  useContext,
  useState,
  type ReactNode,
} from "react";


interface RefreshContextValue {
 refreshKey: Number;
  triggerRefresh: () => void;
}


const RefreshContext = createContext<RefreshContextValue | null>(null);


function RefreshProvider({ children }: { children: ReactNode }) {
  const [refreshKey, setRefreshKey] = useState(0);

  const triggerRefresh = useCallback(() => {
    setRefreshKey((prev) => prev + 1);
  }, []);

  return (
    <RefreshContext.Provider value={{ refreshKey, triggerRefresh }}>
        {children}
    </RefreshContext.Provider>
  );
}


function useRefresh(): RefreshContextValue {
  const ctx = useContext(RefreshContext);
  if (!ctx) {
    throw new Error("useRefresh must be used inside <RefreshProvider>");
  }
  return ctx;
}

export {useRefresh, RefreshProvider};