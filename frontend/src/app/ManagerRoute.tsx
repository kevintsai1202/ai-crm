import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * 業務分析路由守衛：MANAGER 或 ADMIN 可進入，其餘導回儀表板。
 * 函式級註解：用於 /team 路由，前端先擋一層；後端 /api/manager/** 亦以 RBAC 限制（雙重防護）。
 */
export function ManagerRoute() {
  const { user } = useAuth();
  const allowed = user?.role === "MANAGER" || user?.role === "ADMIN";
  return allowed ? <Outlet /> : <Navigate to="/dashboard" replace />;
}
