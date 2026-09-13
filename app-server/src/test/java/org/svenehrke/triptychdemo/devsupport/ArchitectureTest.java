package org.svenehrke.triptychdemo.devsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One concern is checked here that module-per-technology cannot enforce by itself (see concepts.md
 * "Cons"): cross-feature access (e.g. a fruit adapter reaching into beverage-only core types). Maven
 * modules cannot see this at all, since all features share the same core module. "Products"-style
 * aggregators (AdminReceiver, ProductApiReceiver, ProductsHandler, PurchaseHandler, ...) are exempt by
 * construction: they live outside the per-feature packages that the rule scans.
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
	private static final String PKG_CORE = PKG_ROOT + ".core";
	private static final String PKG_EXTERNAL = PKG_ROOT + ".external";
	private static final String PKG_ADAPTER_INBOUND = PKG_ROOT + ".adapter.inbound";
	private static final String PKG_ADAPTER_OUTBOUND = PKG_ROOT + ".adapter.outbound";
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

	/**
	 * One entry per feature that is restricted to its own adapter package, exactly as spelled there
	 * (e.g. "..fruit.."). This is the single place to touch when such a feature is added, renamed or
	 * removed - everything else in this test is derived from this list. Matching against core type names
	 * (see below) is done case-insensitively, so e.g. "nonfood" still matches "NonFoodAPI" without
	 * needing its own spelling.
	 */
	private static final List<String> RESTRICTED_FEATURE_PACKAGES = List.of(
		"fruit", "vegetable", "dairy", "beverage", "meat", "bakery", "nonfood"
	);

	/**
	 * Name stems - lowercase, matching RESTRICTED_FEATURE_PACKAGES above - of core features that are
	 * cross-cutting by design and therefore legitimately reachable from every restricted feature
	 * (inventory bookkeeping, the audit trail, the cross-feature product listing, and checkout/purchase).
	 * A core type counts as shared if its simple name contains any of these stems, case-insensitively -
	 * e.g. adding "inventory" here covers InventoryAPI, InventoryRepositorySPI and InventoryHandler
	 * alike, with no need to list the three concrete type names separately. Add a new stem here whenever
	 * a new cross-cutting feature is introduced in core.
	 */
	private static final Set<String> CROSS_CUTTING_FEATURE_NAME_STEMS = Set.of(
		"inventory", "auditlog", "product", "purchase"
	);

	/**
	 * Pure syntactic containers (the nested-interface holders APIs/SPIs) - not a feature, just naming
	 * infrastructure, so always allowed regardless of feature.
	 */
	private static final Set<String> CORE_CONTAINER_TYPE_NAMES = Set.of("APIs", "SPIs");

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
		classes().that().haveNameMatching(PKG_CORE + ".*API").should().beInterfaces().check(importedClasses);
		classes().that().haveNameMatching(PKG_CORE + ".*API").should().bePublic().check(importedClasses);
		classes().that().haveNameMatching(PKG_CORE + ".*SPI").should().beInterfaces().check(importedClasses);
		classes().that().haveNameMatching(PKG_CORE + ".*SPI").should().bePublic().check(importedClasses);

		classes().that().haveNameMatching(".*Receiver").should().resideInAPackage(PKG_ADAPTER_INBOUND + "..").check(importedClasses);
		classes().that().haveNameMatching(".*Handler").should().resideInAPackage(PKG_CORE + "..").check(importedClasses);
		classes().that().haveNameMatching(".*Service").should().resideInAPackage(PKG_ADAPTER_OUTBOUND + "..").check(importedClasses);
	}

	private static List<String> restrictedFeaturePackages() {
		return RESTRICTED_FEATURE_PACKAGES;
	}

	private static String capitalize(String word) {
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	/**
	 * The module-per-technology Maven cut (see concepts.md) prevents violations of the hexagonal
	 * layering, but cannot see across features inside the shared core module: nothing stops a
	 * fruit-only adapter class from injecting BeveragesAPI. This rule closes that gap: any adapter class
	 * living in a restricted feature's package may only reach core types belonging to that same feature,
	 * or to the shared/cross-cutting core types (inventory, audit log, products, purchase). Aggregators
	 * like AdminReceiver/ProductApiReceiver/ProductsHandler live outside any restricted feature's
	 * package, so this rule does not apply to them - no explicit exception needed.
	 */
	@ParameterizedTest
	@MethodSource("restrictedFeaturePackages")
	void adapter_classes_must_not_access_other_features_core_types(String featurePackage) {
		noClasses().that().resideInAPackage(".." + featurePackage + "..")
			.should().dependOnClassesThat(newCoreTypeOfOtherFeaturePredicate(featurePackage))
			.check(importedClasses);
	}

	private DescribedPredicate<JavaClass> newCoreTypeOfOtherFeaturePredicate(String featurePackage) {
		return new DescribedPredicate<>(
			"reside in core and belong to a different feature than '" + capitalize(featurePackage) + "'") {
			@Override
			public boolean test(JavaClass javaClass) {
				String simpleName = javaClass.getSimpleName();
				String simpleNameLower = simpleName.toLowerCase(Locale.ROOT);
				return javaClass.getPackageName().startsWith(PKG_CORE)
					&& !simpleNameLower.contains(featurePackage)
					&& !CORE_CONTAINER_TYPE_NAMES.contains(simpleName)
					&& CROSS_CUTTING_FEATURE_NAME_STEMS.stream().noneMatch(simpleNameLower::contains);
			}
		};
	}
}
