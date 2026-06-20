package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * 教學版 RAG 文件片段，用一般欄位模擬 pgvector 檢索結果。
 */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument extends AuditableEntity {

    /** 文件片段主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 文件類型，例如 PRODUCT / POLICY / PLAYBOOK。 */
    @Column(name = "doc_type", nullable = false)
    private String docType;

    /** 文件標題。 */
    @Column(nullable = false)
    private String title;

    /** 文件內容片段。 */
    @Column(nullable = false, length = 4000)
    private String content;

    /** 教學版相似度提示值。 */
    @Column(name = "similarity_hint", nullable = false, precision = 5, scale = 2)
    private BigDecimal similarityHint;

    protected KnowledgeDocument() {
    }

    public Long getId() { return id; }
    public String getDocType() { return docType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public BigDecimal getSimilarityHint() { return similarityHint; }
}

