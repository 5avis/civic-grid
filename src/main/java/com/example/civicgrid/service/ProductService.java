package com.example.civicgrid.service;

import com.example.civicgrid.entity.Product;
import com.example.civicgrid.entity.ProductType;
import com.example.civicgrid.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public Product create(Product product) {
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Product> getAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Product> getByType(ProductType type) {
        return productRepository.findByType(type);
    }

    public Product update(Long id, Product updated) {
        Product existing = getById(id);
        existing.setName(updated.getName());
        existing.setType(updated.getType());
        existing.setUnitPrice(updated.getUnitPrice());
        return productRepository.save(existing);
    }

    public void delete(Long id) {
        Product existing = getById(id);
        productRepository.delete(existing);
    }
}
