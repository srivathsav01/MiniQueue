import { ROUTE_CONSTANTS, Routes } from "@/lib/routes";
import { Menu, Moon, Ship, Sun, X } from "lucide-react";
import { useState } from "react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";

function Header() {
    const navigate = useNavigate();
    const {pathname} = useLocation();
    const [isMenuOpen, setIsMenuOpen] = useState(false);

    return (
    <div className="item-center flex justify-center">
        <div className="header bg-bgprimary text-textprimary p-3 w-full md:w-3/4 m-2 flex justify-between items-center mt-2 rounded-2xl border border-[#4F4F4F] dark:border-[#dddddd]">
            <span className="flex items-center gap-2 font-semibold font-serif text-lg cursor-pointer" onClick={() => navigate('/' + ROUTE_CONSTANTS.HOME)}>
                <Ship size={30} strokeWidth={1} className="ml-1.5"/>
                MiniQueue
            </span>
            <div className="nav w-64 hidden md:block">
                    <ul className="flex flex-col md:flex-row gap-6 text-lg font-extralight">
                        {Routes.map(({ path, label }) => {
                            const isActive = path === "/home" || path === "/"
                                ? pathname === "/" || pathname === "/home"
                                : pathname === path;
                            return (
                                <li key={path} className={`relative cursor-pointer ${isActive ? "text-textprimary font-semibold underline underline-offset-3" : ""}`}>
                                    <NavLink
                                        to={path}
                                        className="flex flex-col items-start hover:text-textsecondary transition duration-200"
                                    >
                                        {label}
                                    </NavLink>
                                </li>
                            );
                        })}
                    </ul>
                </div>
            <div className="actions flex items-center justify-end w-1/5">
                <button className="md:hidden text-textprimary cursor-pointer ml-2" onClick={()=>{setIsMenuOpen(!isMenuOpen)}}>
                    {isMenuOpen ? <X size={20} /> : <Menu size={20} />}
                </button>
                <button className="text-textprimary cursor-pointer ml-2" onClick={()=>{document.documentElement.classList.toggle('dark')}}>
                    <Sun className="dark:hidden" />
                    <Moon className="hidden dark:block" />
                </button>
            </div>
            {isMenuOpen && (
                <div className={`mobile-nav absolute top-16 right-0 w-64 bg-card text-textprimary p-4 rounded-lg shadow-lg transition-transform duration-300 z-20 ${isMenuOpen ? "translate-x-0" : "translate-x-full"} md:hidden`}>
                    <ul className="flex flex-col gap-4 text-lg font-extralight">
                        {Routes.map(({ path, label }) => {
                            const isActive = path === "/home" || path === "/"
                                ? pathname === "/" || pathname === "/home"
                                : pathname === path;
                        return (
                            <li key={path} className={`relative cursor-pointer ${isActive ? "text-textprimary font-semibold underline underline-offset-3" : ""}`}>
                                <NavLink
                                    to={path}
                                    onClick={() => setIsMenuOpen(false)}
                                    className="flex flex-col items-start hover:text-textsecondary transition duration-200"
                                >
                                    {label}
                                </NavLink>
                            </li>
                        );
                    })}
                </ul>
            </div>)}
        </div>
    </div>
    );
}

export default Header;