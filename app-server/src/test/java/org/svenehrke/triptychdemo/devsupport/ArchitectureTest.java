package org.svenehrke.triptychdemo.devsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * One concern is checked here that module-per-technology cannot enforce by itself (see concepts.md
 * "Cons"): cross-feature access. Every restricted feature (fruit, vegetable, dairy, beverage, meat,
 * bakery, nonfood) lives under its own "..feature.&lt;name&gt;.." package, in every module that touches
 * it (core and every adapter module alike) - so an ArchUnit slices rule can check "no two features
 * depend on each other" directly from the package structure, with zero maintained list of feature names.
 * Anything cross-cutting (inventory, audit log, products, purchase, cashpoint, the admin/shop/json-api
 * aggregators) lives under "..cross.." instead, which never matches the "feature.(*)" slice pattern and
 * is therefore exempt from the check by construction - no explicit exception needed for e.g. AdminReceiver
 * reaching every commodity's ordering API.
 * <p>
 * Naming conventions are checked too, mostly belt-and-suspenders since the Maven module graph already
 * makes most violations of them uncompilable. The inward-only dependency direction of core is NOT
 * checked here: core's pom.xml has no dependency on any adapter module, so referencing an adapter class
 * from core is a compile error, not something ArchUnit needs to guard against.
 * <p>
 * external-* modules simulate systems outside the hexagon entirely (see concepts.md) and are excluded
 * from the scan altogether, not just from individual rules.
 */
class ArchitectureTest {
	private static final String PKG_ROOT = "org.svenehrke.triptychdemo";
	private static final String PKG_FEATURE = PKG_ROOT + ".feature";
	private static final String PKG_CROSS = PKG_ROOT + ".cross";
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
	void classname_determines_package() {
		classes().that().haveNameMatching(".*API").should().beInterfaces().check(importedClasses);
		classes().that().haveNameMatching(".*API").should().bePublic().check(importedClasses);
		classes().that().haveNameMatching(".*SPI").should().beInterfaces().check(importedClasses);
		classes().that().haveNameMatching(".*SPI").should().bePublic().check(importedClasses);

		// Every port, use-case and adapter class is filed under a restricted feature or a cross-cutting
		// concern - nothing is left loose at some other, undeclared package.
		classes().that().haveNameMatching(".*(API|SPI|Handler|Receiver|Service)")
			.should().resideInAnyPackage(PKG_FEATURE + "..", PKG_CROSS + "..")
			.check(importedClasses);
	}

	/**
	 * The module-per-technology Maven cut (see concepts.md) prevents violations of the hexagonal
	 * layering, but cannot see across features inside the shared core module: nothing stops a
	 * fruit-only adapter class from injecting BeveragesAPI. This single slices rule closes that gap for
	 * every restricted feature at once, purely from the package structure - see the class Javadoc.
	 */
	@Test
	void features_do_not_depend_on_each_other() {
		slices().matching(PKG_FEATURE + ".(*)..")
			.should().notDependOnEachOther()
			.check(importedClasses);
	}
}
