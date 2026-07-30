package com.example.hexarcdemo.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestInventoryHelper {

    @Inject EntityManager em;

    @Transactional
    public void resetInventory() {
        em.createNativeQuery("DELETE FROM products").executeUpdate();
    }
}
