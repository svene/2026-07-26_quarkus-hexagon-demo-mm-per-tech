package org.svenehrke.triptychdemo.cross.occupancy;

import org.svenehrke.triptychdemo.cross.auditlog.AuditLogEntry;
import org.svenehrke.triptychdemo.cross.auditlog.AuditLogSPI;
import org.svenehrke.triptychdemo.cross.location.Locations;
import org.svenehrke.triptychdemo.cross.location.Store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoTillsHandlerTest {

	private final List<StoreOccupancy> stored = new ArrayList<>();
	private final List<TillCount> sent = new ArrayList<>();
	private final List<String> audit = new ArrayList<>();
	private final AutoTillsHandler handler = new AutoTillsHandler();
	private RuntimeException checkoutSystemFailure;

	@BeforeEach
	void setUp() {
		handler.repository = new OccupancyRepositorySPI() {
			@Override public boolean saveIfNewer(StoreOccupancy occupancy) { throw new UnsupportedOperationException(); }
			@Override public List<StoreOccupancy> findAll() { return List.copyOf(stored); }
			@Override public void appendToHistory(StoreOccupancy occupancy, Instant keepSince) { throw new UnsupportedOperationException(); }
			@Override public List<StoreOccupancy> history(Store store, Instant since) { throw new UnsupportedOperationException(); }
		};
		handler.checkoutSystem = tillCount -> {
			if (checkoutSystemFailure != null) throw checkoutSystemFailure;
			sent.add(tillCount);
		};
		handler.auditLog = new AuditLogSPI() {
			@Override public void log(String event, String details) { audit.add(event + " " + details); }
			@Override public List<AuditLogEntry> findRecent(int limit) { throw new UnsupportedOperationException(); }
			@Override public void clear() { throw new UnsupportedOperationException(); }
		};
	}

	/** Bern (capacity 6) with one busy till and two queuing, measured {@code secondsFromNow} from now. */
	private void report(long secondsFromNow, int tills) {
		stored.clear();
		stored.add(new StoreOccupancy(Locations.BERN, Instant.now().plusSeconds(secondsFromNow), 6, 6, 4, tills, tills, 0, 0));
	}

	@Test
	void sendsThePolicysDecisionToTheCheckoutSystem() {
		report(0, 1);

		handler.adjust(Locations.BERN);

		assertThat(sent).containsExactly(new TillCount(Locations.BERN, 2));
		assertThat(audit).containsExactly("AutoTillsHandler: TILLS_OPENED bern: 1→2, queuing 4, tills busy 1, inside 6/6");
	}

	@Test
	void afterAChange_waitsForAReportMeasuredAfterTheCooldown() {
		report(0, 1);
		handler.adjust(Locations.BERN);

		report(AutoTillsHandler.COOLDOWN.toSeconds() - 2, 2);
		handler.adjust(Locations.BERN);
		assertThat(sent).hasSize(1);

		report(AutoTillsHandler.COOLDOWN.toSeconds() + 2, 2);
		handler.adjust(Locations.BERN);
		assertThat(sent).containsExactly(new TillCount(Locations.BERN, 2), new TillCount(Locations.BERN, 3));
	}

	@Test
	void theCooldownIsPerStore() {
		report(0, 1);
		handler.adjust(Locations.BERN);
		stored.add(new StoreOccupancy(Locations.BASEL, Instant.now(), 10, 10, 3, 2, 2, 0, 0));

		handler.adjust(Locations.BASEL);

		assertThat(sent).containsExactly(new TillCount(Locations.BERN, 2), new TillCount(Locations.BASEL, 3));
	}

	@Test
	void aRefusedChange_isLoggedAndStartsNoCooldown() {
		report(0, 1);
		checkoutSystemFailure = new IllegalStateException("unreachable");
		handler.adjust(Locations.BERN);
		checkoutSystemFailure = null;

		handler.adjust(Locations.BERN);

		assertThat(audit).containsExactly(
			"AutoTillsHandler: TILLS_CHANGE_FAILED bern: 1→2, queuing 4, tills busy 1, inside 6/6: unreachable",
			"AutoTillsHandler: TILLS_OPENED bern: 1→2, queuing 4, tills busy 1, inside 6/6");
		assertThat(sent).containsExactly(new TillCount(Locations.BERN, 2));
	}

	@Test
	void withoutAReport_nothingHappens() {
		handler.adjust(Locations.BERN);

		assertThat(sent).isEmpty();
		assertThat(audit).isEmpty();
	}
}
