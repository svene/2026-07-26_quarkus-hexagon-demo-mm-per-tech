package org.svenehrke.triptychdemo.devsupport;

import static org.svenehrke.triptychdemo.devsupport.TriptychArchitecture.triptychArchitecture;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Checks this project's "Triptych" architecture (see {@link TriptychArchitecture} and concepts.md) -
 * cross-feature access is the one concern module-per-technology cannot enforce by itself (see concepts.md
 * "Cons"): the Maven module graph prevents violations of the hexagonal layering, but cannot see across
 * features inside the shared core module, since all commodities share it. Every restricted feature (fruit,
 * vegetable, dairy, beverage, meat, bakery, nonfood) lives under its own "feature.&lt;name&gt;" package in
 * every module that touches it, so this comes down to a single package-based check, with zero maintained
 * list of feature names. Cross-cutting concerns (inventory, audit log, products, purchase, cashpoint, the
 * admin/shop/json-api aggregators) live under "cross" instead, exempt by construction.
 * <p>
 * The inward-only dependency direction of core is NOT checked here: core's pom.xml has no dependency on
 * any adapter module, so referencing an adapter class from core is a compile error already.
 * <p>
 * external-* modules simulate systems outside the hexagon entirely (see concepts.md) and are excluded
 * from the scan altogether, not just from individual rules.
 */
class ArchitectureTest {
	private static final String PKG_ROOT = "org.svenehrke.triptychdemo";
	private static final String PKG_EXTERNAL = PKG_ROOT + ".external";
	private static final String GROUP_ID_REPO_PATH = "/org/svenehrke/";

	/**
	 * Skips opening third-party library JARs (Quarkus, Jakarta, Kafka clients, ...) during the scan.
	 * Our own reactor-module JARs (core, every adapter module) are resolved from the local repo under
	 * this project's groupId path and stay included - that's what makes them visible at all (see the
	 * note on DO_NOT_INCLUDE_JARS below). Purely a scan-time optimization: importPackages(PKG_ROOT)
	 * already discards every non-project class regardless, so this does not change what gets imported.
	 */
	private static final ImportOption EXCLUDE_THIRD_PARTY_JARS =
		location -> !location.isJar() || location.contains(GROUP_ID_REPO_PATH);

	/**
	 * external-* modules are not part of the hexagonal architecture (see concepts.md) - they are
	 * excluded from the scan entirely rather than merely exempted rule by rule.
	 */
	private static final ImportOption EXCLUDE_EXTERNAL_MODULES =
		location -> !location.contains("/" + PKG_EXTERNAL.replace('.', '/') + "/");

	JavaClasses importedClasses;

	@BeforeEach
	void beforeEach() {
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForMemberTypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForAccessToTypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForSupertypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForEnclosingTypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForGenericSignatureTypes", "0");
		ArchConfiguration.get().setProperty("archRule.failOnEmptyShould", "false");

		// NOTE: every adapter module (core included) reaches app-server as a Maven JAR dependency,
		// not as source - the predefined DO_NOT_INCLUDE_JARS/ARCHIVES options would exclude exactly the
		// classes this test needs to see, leaving only app-server's own classes and making every rule
		// vacuous. EXCLUDE_THIRD_PARTY_JARS/EXCLUDE_EXTERNAL_MODULES only skip non-project resp.
		// non-hexagon classes, so this stays safe.
		importedClasses = new ClassFileImporter(Arrays.asList(
			ImportOption.Predefined.DO_NOT_INCLUDE_TESTS,
			EXCLUDE_THIRD_PARTY_JARS,
			EXCLUDE_EXTERNAL_MODULES
		))
			.importPackages(PKG_ROOT)
		;
	}

	@Test
	void triptych_architecture_is_respected() {
		triptychArchitecture(PKG_ROOT).check(importedClasses);
	}
}
