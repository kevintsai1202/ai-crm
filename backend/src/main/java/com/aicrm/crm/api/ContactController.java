package com.aicrm.crm.api;

import com.aicrm.crm.repository.ContactRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聯絡人 REST API，提供編輯與刪除（新增掛在 /api/customers/{id}/contacts）。
 */
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    /** 聯絡人資料存取介面。 */
    private final ContactRepository contactRepository;

    public ContactController(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    /**
     * 編輯聯絡人。
     *
     * @param id 聯絡人 ID
     * @param request 編輯請求
     * @return 更新後的聯絡人 DTO
     */
    @PutMapping("/{id}")
    @Transactional
    public Dtos.ContactResponse update(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateContactRequest request) {
        var contact = contactRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("查無此聯絡人：" + id));
        contact.updateInfo(request.name(), request.title(), request.email());
        contactRepository.save(contact);
        return new Dtos.ContactResponse(contact.getId(), contact.getName(), contact.getTitle(), contact.getEmail());
    }

    /**
     * 刪除聯絡人。
     *
     * @param id 聯絡人 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        contactRepository.deleteById(id);
    }
}
