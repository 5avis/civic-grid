package com.example.civicgrid.repository;

import com.example.civicgrid.entity.Contact;
import com.example.civicgrid.entity.ContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {
    Optional<Contact> findByName(String name);
    List<Contact> findByType(ContactType type);
}
