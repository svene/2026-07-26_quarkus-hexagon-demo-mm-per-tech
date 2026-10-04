package org.svenehrke.triptychdemo.cross.purchasing;

import org.svenehrke.triptychdemo.cross.products.ProductType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SupplierOrderTest {

	@Test
	void outstanding_onlyWhileOpen() {
		assertThat(order(SupplierOrderStatus.OPEN).outstanding()).isEqualTo(6);
		assertThat(order(SupplierOrderStatus.CANCELLED).outstanding()).isZero();
		assertThat(order(SupplierOrderStatus.DELIVERED).outstanding()).isZero();
	}

	@Test
	void statusAndOrigin_roundTripThroughTheirNames() {
		for (var status : List.of(SupplierOrderStatus.OPEN, SupplierOrderStatus.DELIVERED, SupplierOrderStatus.CANCELLED)) {
			assertThat(SupplierOrderStatus.of(status.name())).isEqualTo(status);
		}
		for (var origin : List.of(SupplierOrderOrigin.MANUAL, SupplierOrderOrigin.AUTOMATIC)) {
			assertThat(SupplierOrderOrigin.of(origin.name())).isEqualTo(origin);
		}
		assertThatIllegalArgumentException().isThrownBy(() -> SupplierOrderStatus.of("LOST"));
	}

	@Test
	void describe() {
		assertThat(order(SupplierOrderStatus.OPEN).describe()).isEqualTo("supplier order 7 Apple 4/10 OPEN AUTOMATIC");
	}

	private static SupplierOrder order(SupplierOrderStatus status) {
		return new SupplierOrder(7, "Apple", ProductType.FRUIT, 10, 4, status, SupplierOrderOrigin.AUTOMATIC, Instant.EPOCH);
	}
}
