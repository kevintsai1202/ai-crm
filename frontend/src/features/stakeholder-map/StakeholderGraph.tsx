import type { StakeholderRelationDto, StakeholderRoleDto } from "../../types";
import { edgeStyle, nodeLabel } from "./stakeholderState";
import { useTranslation } from "react-i18next";

/** 決策鏈圖屬性。 */
interface StakeholderGraphProps {
  /** 已確認角色節點。 */
  roles: StakeholderRoleDto[];
  /** 已確認關係邊。 */
  relations: StakeholderRelationDto[];
}

/** 已確認決策鏈圖：角色為節點、關係為邊，全部以實線呈現（事實）。 */
export function StakeholderGraph({ roles, relations }: StakeholderGraphProps) {
  const { t } = useTranslation("operations");
  return (
    <div data-testid="sm-confirmed-graph" style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <div>
        <div style={{ fontSize: 13, fontWeight: 700, color: "#475569", marginBottom: 6 }}>{t("stakeholder.roles")}</div>
        {roles.length === 0 && <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>{t("stakeholder.emptyRoles")}</p>}
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {roles.map((role) => (
            <div key={role.id} data-testid={`sm-role-${role.contactId}`} style={{
              border: `2px solid #16a34a`, borderRadius: 8, padding: "8px 12px", background: "#f0fdf4", minWidth: 160,
            }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: "#122232" }}>{nodeLabel(role.contactName, role.contactTitle)}</div>
              <div style={{ fontSize: 11, color: "#166534", marginTop: 2 }}>
                {role.roleType} · {t("stakeholder.influence", { value: role.influence })} · {t("stakeholder.stance", { value: role.stance })}
              </div>
            </div>
          ))}
        </div>
      </div>
      <div>
        <div style={{ fontSize: 13, fontWeight: 700, color: "#475569", marginBottom: 6 }}>{t("stakeholder.relations")}</div>
        {relations.length === 0 && <p style={{ fontSize: 13, color: "#94a3b8", margin: 0 }}>{t("stakeholder.emptyRelations")}</p>}
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {relations.map((relation) => (
            <div key={relation.id} style={{ fontSize: 13, color: "#334155", display: "flex", alignItems: "center", gap: 8 }}>
              <span>{relation.fromContactName}</span>
              <span style={{ borderTop: `2px ${edgeStyle(relation.status)} #16a34a`, width: 40, display: "inline-block" }} />
              <span style={{ fontSize: 11, color: "#64748b" }}>{relation.relationType}</span>
              <span style={{ borderTop: `2px ${edgeStyle(relation.status)} #16a34a`, width: 40, display: "inline-block" }} />
              <span>{relation.toContactName}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
