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
import java.util.Map;
import java.util.Set;

/**
 * Two concerns are checked here, matching what module-per-technology cannot enforce by itself
 * (see concepts.md "Cons"):
 * <p>
 * 1) naming conventions and the inward-only dependency direction of the core - mostly belt-and-suspenders,
 * since the Maven module graph already makes most violations of these uncompilable.
 * <p>
 * 2) cross-feature access between commodities (e.g. a fruit adapter reaching into beverage-only core
 * types) - Maven modules cannot see this at all, since all commodities share the same core module.
 * "Products"-style aggregators (AdminReceiver, ProductApiReceiver, ProductsHandler, PurchaseHandler, ...)
 * are exempt from (2) by construction: they live outside the per-commodity packages that the rule scans.
 */
class ArchitectureTest {
	private static final String PKG_ROOT = "org.svenehrke.triptychdemo";
	private static final String PKG_CORE = PKG_ROOT + ".core";
	private static final String PKG_EXTERNAL = PKG_ROOT + ".external";
	private static final String PKG_ADAPTER_INBOUND = PKG_ROOT + ".adapter.inbound";
	private static final String PKG_ADAPTER_OUTBOUND = PKG_ROOT + ".adapter.outbound";

	/**
	 * Package name (as used by the adapter modules, e.g. "..fruit..") to the name stem shared by every
	 * core type belonging to that commodity (e.g. "FruitsAPI", "FruitSupplierSPI", "FruitDelivery", "FruitsHandler").
	 */
	private static final Map<String, String> COMMODITY_PACKAGE_TO_CORE_NAME_STEM = Map.of(
		"fruit", "Fruit",
		"vegetable", "Vegetable",
		"dairy", "Dairy",
		"beverage", "Beverage",
		"meat", "Meat",
		"bakery", "Bakery",
		"nonfood", "NonFood"
	);

	/**
	 * Core types that are legitimately shared across every commodity (inventory bookkeeping, the audit
	 * trail, the cross-commodity product listing/purchase, and the enclosing container interfaces).
	 */
	private static final Set<String> SHARED_CORE_TYPE_NAMES = Set.of(
		"APIs", "SPIs",
		"InventoryAPI", "InventoryRepositorySPI", "InventoryHandler",
		"AuditLogAPI", "AuditLogSPI", "AuditLogHandler", "AuditLogEntry",
		"Product", "ProductType", "ProductsAPI", "ProductsHandler",
		"PurchaseAPI", "PurchaseHandler", "PurchaseItem"
	);

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
		// not as source - DO_NOT_INCLUDE_JARS/ARCHIVES would exclude exactly the classes this test
		// needs to see, leaving only app-server's own classes and making every rule vacuous.
		importedClasses = new ClassFileImporter(Arrays.asList(
			ImportOption.Predefined.DO_NOT_INCLUDE_TESTS
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

		// external-* modules simulate systems outside the hexagon (see concepts.md) and are exempt
		// from these naming rules, e.g. the duplicated SOAP SEI *OrderService classes.
		classes().that().haveNameMatching(".*Receiver").and().resideOutsideOfPackage(PKG_EXTERNAL + "..")
			.should().resideInAPackage(PKG_ADAPTER_INBOUND + "..").check(importedClasses);

		classes().that().haveNameMatching(".*Handler").and().resideOutsideOfPackage(PKG_EXTERNAL + "..")
			.should().resideInAPackage(PKG_CORE + "..").check(importedClasses);

		classes().that().haveNameMatching(".*Service").and().resideOutsideOfPackage(PKG_EXTERNAL + "..")
			.should().resideInAPackage(PKG_ADAPTER_OUTBOUND + "..").check(importedClasses);
	}

	@Test
	void only_inwards() {
		noClasses().that().resideInAPackage(PKG_CORE + "..").should()
			.dependOnClassesThat().resideInAnyPackage(PKG_ADAPTER_INBOUND + "..", PKG_ADAPTER_OUTBOUND + "..")
			.check(importedClasses);
	}

	private static Set<String> commodityPackageNames() {
		return COMMODITY_PACKAGE_TO_CORE_NAME_STEM.keySet();
	}

	/**
	 * The module-per-technology Maven cut (see concepts.md) prevents violations of the hexagonal
	 * layering, but cannot see across commodities inside the shared core module: nothing stops a
	 * fruit-only adapter class from injecting BeveragesAPI. This rule closes that gap: any adapter
	 * (or external stub) class living in a per-commodity package may only reach core types belonging
	 * to that same commodity, or to the shared/common core types (inventory, audit log, products,
	 * purchase). Aggregators like AdminReceiver/ProductApiReceiver/ProductsHandler live outside any
	 * per-commodity package, so this rule does not apply to them - no explicit exception needed.
	 */
	@ParameterizedTest
	@MethodSource("commodityPackageNames")
	void adapter_classes_must_not_access_other_commodities_core_types(String commodityPackage) {
		String nameStem = COMMODITY_PACKAGE_TO_CORE_NAME_STEM.get(commodityPackage);

		noClasses().that().resideInAPackage(".." + commodityPackage + "..")
			.should().dependOnClassesThat(newCoreTypeOfOtherCommodityPredicate(nameStem))
			.check(importedClasses);
	}

	private DescribedPredicate<JavaClass> newCoreTypeOfOtherCommodityPredicate(String nameStem) {
		return new DescribedPredicate<>(
			"reside in core and belong to a different commodity than '" + nameStem + "'") {
			@Override
			public boolean test(JavaClass javaClass) {
				return javaClass.getPackageName().startsWith(PKG_CORE)
					&& !javaClass.getSimpleName().contains(nameStem)
					&& !SHARED_CORE_TYPE_NAMES.contains(javaClass.getSimpleName());
			}
		};
	}
}
