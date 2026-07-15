import { Navigate, Route, Routes } from "react-router-dom";
import { AppShell } from "./app/AppShell";
import { ProtectedRoute } from "./app/ProtectedRoute";
import { AdminRoute } from "./app/AdminRoute";
import { ManagerRoute } from "./app/ManagerRoute";
import { LoginPage } from "./features/auth/LoginPage";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { CustomersPage } from "./features/customers/CustomersPage";
import { BusinessCardWizardPage } from "./features/business-card/BusinessCardWizardPage";
import { MeetingCopilotPage } from "./features/meeting-copilot/MeetingCopilotPage";
import { AdminUsersPage } from "./features/admin/AdminUsersPage";
import AdminSettingsPage from "./features/admin/AdminSettingsPage";
import { TeamAnalyticsPage } from "./features/team/TeamAnalyticsPage";

/**
 * 應用路由表：登入頁公開；其餘頁面需登入並套用 AppShell（側邊欄 + Outlet）。
 * 函式級註解：儀表板（/dashboard）與操作（/customers）為兩個獨立路由頁，達成儀表板與操作分頁。
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          {/* 我的工作台已併入客戶工作台；舊連結導向客戶工作台 */}
          <Route path="/my-work" element={<Navigate to="/customers" replace />} />
          <Route path="/customers" element={<CustomersPage />} />
          <Route path="/customers/:id" element={<CustomersPage />} />
          <Route path="/business-cards/new" element={<BusinessCardWizardPage />} />
          <Route path="/customers/:customerId/meeting-copilot" element={<MeetingCopilotPage />} />
          <Route element={<ManagerRoute />}>
            <Route path="/team" element={<TeamAnalyticsPage />} />
          </Route>
          <Route element={<AdminRoute />}>
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/settings" element={<AdminSettingsPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
