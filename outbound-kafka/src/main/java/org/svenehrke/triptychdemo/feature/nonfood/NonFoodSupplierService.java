package org.svenehrke.triptychdemo.feature.nonfood;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@ApplicationScoped
public class NonFoodSupplierService implements NonFoodSupplierSPI {

    @Inject
    @Channel("nonfood-orders-out")
    Emitter<NonFoodOrderMessage> emitter;

    @Override
    public void placeOrder(NonFoodOrder nonFoodOrder) {
        emitter.send(new NonFoodOrderMessage(nonFoodOrder.productName(), nonFoodOrder.quantity()));
    }
}
