package com.example.civicgrid.service;

import com.example.civicgrid.entity.Contact;
import com.example.civicgrid.entity.ContactType;
import com.example.civicgrid.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;

    public Contact create(Contact contact) {
        return contactRepository.save(contact);
    }

    @Transactional(readOnly = true)
    public Contact getById(Long id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Contact> getAll() {
        return contactRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Contact> getByType(ContactType type) {
        return contactRepository.findByType(type);
    }

    public Contact update(Long id, Contact updated) {
        Contact existing = getById(id);
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setEmail(updated.getEmail());
        existing.setPhone(updated.getPhone());
        existing.setAddress(updated.getAddress());
        return contactRepository.save(existing);
    }

    public void delete(Long id) {
        Contact existing = getById(id);
        contactRepository.delete(existing);
    }
}
