package org.svenehrke.triptychdemo.cross.occupancy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class CheckoutSystemService implements CheckoutSystemSPI {

    @Inject
    @RestClient
    CheckoutSystemClient client;

    @Override
    public void setTills(TillCount tillCount) {
        client.setTills(tillCount.store().id(), new CheckoutSystemClient.TillsRequest(tillCount.tills()));
    }
}
