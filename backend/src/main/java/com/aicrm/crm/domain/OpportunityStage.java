package com.aicrm.crm.domain;

/**
 * 商機階段，用於 Kanban 看板與風險路徑判斷。
 */
public enum OpportunityStage {
    QUALIFICATION,
    PROPOSAL,
    NEGOTIATION,
    CLOSED_WON,
    CLOSED_LOST
}

