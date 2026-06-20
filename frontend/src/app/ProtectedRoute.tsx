import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

/**
 * 路由守衛：未登入（無 token）導向 /login。
 * 函式級註解：透過 useAuth 訂閱全域認證狀態，登入/登出/401 時都會重新判斷並導向，
 * 避免直接讀模組函式 getToken() 而無法在登出當下重渲染的問題。
 */
export function ProtectedRoute() {
  const { isAuthed } = useAuth();
  return isAuthed ? <Outlet /> : <Navigate to="/login" replace />;
}
