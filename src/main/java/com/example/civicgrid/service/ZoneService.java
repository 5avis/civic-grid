package com.example.civicgrid.service;

import com.example.civicgrid.entity.Zone;
import com.example.civicgrid.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public Zone create(Zone zone) {
        return zoneRepository.save(zone);
    }

    @Transactional(readOnly = true)
    public Zone getById(Long id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Zone> getAll() {
        return zoneRepository.findAll();
    }

    public Zone update(Long id, Zone updated) {
        Zone existing = getById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        return zoneRepository.save(existing);
    }

    public void delete(Long id) {
        Zone existing = getById(id);
        zoneRepository.delete(existing);
    }
}
