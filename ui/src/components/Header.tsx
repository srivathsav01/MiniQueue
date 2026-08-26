import { ROUTE_CONSTANTS, Routes } from "@/lib/routes";
import { Moon, Ship, Sun } from "lucide-react";
import { NavLink, useLocation, useNavigate } from "react-router-dom";

function Header() {
    const navigate = useNavigate();
    const {pathname} = useLocation();
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
                {/* { user && 
                    <div className="relative">
                        <User 
                            className="cursor-pointer" 
                            onClick={() => setIsUserDrawerOpen(!isUserDrawerOpen)}
                        />
                        {isUserDrawerOpen && (
                            <div className="absolute right-0 top-full mt-2 bg-bgprimary border border-[#4F4F4F] dark:border-[#dddddd] rounded-lg shadow-lg p-4 min-w-48 z-50">
                                <div className="flex items-center gap-3 mb-3 pb-3 border-b border-[#4F4F4F] dark:border-[#dddddd]">
                                    <User size={20} />
                                    <div>
                                        <p className="font-medium">{user.name}</p>
                                        <p className="text-sm text-gray-500">{user.email}</p>
                                    </div>
                                </div>
                                <button 
                                    onClick={() => {
                                        setUser(null);
                                        setIsUserDrawerOpen(false);
                                    }}
                                    className="w-full text-left hover:bg-bglight hover:text-textsecondary rounded px-2 py-1 transition">
                                    Logout
                                </button>
                            </div>
                        )}
                    </div>
                } */}
                <button className="text-textprimary cursor-pointer ml-2" onClick={()=>{document.documentElement.classList.toggle('dark')}}>
                    <Sun className="dark:hidden" />
                    <Moon className="hidden dark:block" />
                </button>
            </div>
        </div>
    </div>
    );
}

export default Header;