import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * 管理員路由守衛：僅 ADMIN 角色可進入，其餘導回儀表板。
 * 函式級註解：用於 /admin/* 路由，前端先擋一層；後端 /api/admin/** 亦以 RBAC 限制（雙重防護）。
 */
export function AdminRoute() {
  const { user } = useAuth();
  return user?.role === "ADMIN" ? <Outlet /> : <Navigate to="/dashboard" replace />;
}
