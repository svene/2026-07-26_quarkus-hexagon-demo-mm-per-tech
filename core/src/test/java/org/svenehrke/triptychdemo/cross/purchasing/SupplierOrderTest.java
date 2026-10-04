package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierOrderTest {

	@Test
	void outstanding_onlyWhileOpen() {
		assertThat(order(SupplierOrderStatus.OPEN).outstanding()).isEqualTo(6);
		assertThat(order(SupplierOrderStatus.CANCELLED).outstanding()).isZero();
		assertThat(order(SupplierOrderStatus.DELIVERED).outstanding()).isZero();
	}

	@Test
	void describe() {
		assertThat(order(SupplierOrderStatus.OPEN).describe()).isEqualTo("supplier order 7 Apple 4/10 OPEN AUTOMATIC");
	}

	private static SupplierOrder order(SupplierOrderStatus status) {
		return new SupplierOrder(7, "Apple", ProductType.FRUIT, 10, 4, status, SupplierOrderOrigin.AUTOMATIC, Instant.EPOCH);
	}
}
