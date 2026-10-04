package org.svenehrke.triptychdemo.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class TestInventoryHelper {

    @Inject EntityManager em;

    /** The stock of every location, every replenishment request, shipment and supplier order. */
    @Transactional
    public void resetInventory() {
        em.createNativeQuery("DELETE FROM stock").executeUpdate();
        em.createNativeQuery("DELETE FROM replenishment_request").executeUpdate();
        em.createNativeQuery("DELETE FROM shipment").executeUpdate();
        em.createNativeQuery("DELETE FROM supplier_order").executeUpdate();
    }
}
