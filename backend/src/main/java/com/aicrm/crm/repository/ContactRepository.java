package com.aicrm.crm.repository;

import com.aicrm.crm.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 聯絡人資料存取介面。
 */
public interface ContactRepository extends JpaRepository<Contact, Long> {
}
