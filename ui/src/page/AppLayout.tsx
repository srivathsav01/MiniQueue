import Header from "@/components/Header";
import { Outlet } from "react-router-dom";

export default function Applayout() {
    return (
        <>
        <Header />
        <main className="w-full">
            <Outlet />
        </main>
        </>
    )
}