package com.example.hexademo.devsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

class ArchitectureTest {
	private static String PKG_ROOT = "org.svenehrke.demo";
	private static String PKG_CORE = PKG_ROOT + ".core";
	private static String PKG_INBOUND = PKG_ROOT + ".inbound";
	private static String PKG_OUTBOUND = PKG_ROOT + ".outbound";
	private static final String[] expectedSubpackageNames = {"fruits", "beverages"};

	DescribedPredicate<JavaClass> domainOrExternalLibPredicate =
		JavaClass.Predicates.resideInAnyPackage(PKG_ROOT + "..")
			.or(JavaClass.Predicates.resideOutsideOfPackage(PKG_ROOT));

	JavaClasses importedClasses;

	@BeforeEach
	void beforeEach() {
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForMemberTypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForAccessToTypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForSupertypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForEnclosingTypes", "0");
		ArchConfiguration.get().setProperty("import.dependencyResolutionProcess.maxIterationsForGenericSignatureTypes", "0");
		ArchConfiguration.get().setProperty("archRule.failOnEmptyShould", "false");

		importedClasses = new ClassFileImporter(Arrays.asList(
			ImportOption.Predefined.DO_NOT_INCLUDE_ARCHIVES,
			ImportOption.Predefined.DO_NOT_INCLUDE_JARS,
			ImportOption.Predefined.DO_NOT_INCLUDE_TESTS
		))
			.importPackages(PKG_ROOT)
		;
	}

	@Test
	@Disabled
	void sandwich_architecture() {
		var arch = onionArchitecture()
			.domainModels(PKG_CORE + "..")
			.domainServices(PKG_CORE + "..")
			.applicationServices(PKG_CORE + "..")
			.adapter("outbound", PKG_OUTBOUND + "..")
			.adapter("inbound", PKG_INBOUND + "..")
			;
		arch.check(importedClasses);
	}
	@Test
	void classname_determines_package() {
		classes().that().haveNameMatching(PKG_CORE + ".*API").should().beInterfaces().check(importedClasses);
		classes().that().haveNameMatching(PKG_CORE + ".*API").should().bePublic().check(importedClasses);
		classes().that().haveNameMatching(PKG_CORE + ".*SPI").should().beInterfaces().check(importedClasses);
		classes().that().haveNameMatching(PKG_CORE + ".*SPI").should().bePublic().check(importedClasses);

		classes().that().haveNameMatching(".*Receiver").should().resideInAPackage(PKG_ROOT + ".inbound..").check(importedClasses);

		classes().that().haveNameMatching(".*Handler").should().resideInAPackage(PKG_ROOT + ".core..").check(importedClasses);

		classes().that().haveNameMatching(".*Service").should().resideInAPackage(PKG_ROOT + ".outbound..").check(importedClasses);
	}

	@Test
	void only_inwards() {
		noClasses().that().resideInAPackage(PKG_ROOT + ".core..").should()
			.dependOnClassesThat().resideInAnyPackage(PKG_INBOUND + "..", PKG_OUTBOUND + "..").check(importedClasses);

		classes().that().resideInAPackage(PKG_ROOT + ".core..").should()
			.onlyDependOnClassesThat().resideInAPackage(PKG_ROOT + ".core..")
			.orShould().onlyDependOnClassesThat().resideOutsideOfPackage(PKG_ROOT)
			.check(importedClasses);
	}

	/**
	 * In addition to what the testname says:
	 * This also means that accessing more than one 'component' (e.g. fruits and beverages) is only allowed
	 * from inside the core part parse combining component (e.g.: core/products)
	 */
	@ParameterizedTest
	@ValueSource(strings = {"fruits", "beverages", "products"})
	void only_inwards_inside_component(String pkg) {
		List.of("outbound", "inbound").forEach(inOrOut -> {
			classes().that().resideInAPackage(PKG_ROOT + "." + inOrOut + "." + pkg + "..").should()
				.onlyDependOnClassesThat().resideInAnyPackage(PKG_ROOT + ".core." + pkg + "..")
				.orShould().onlyDependOnClassesThat().resideOutsideOfPackage(PKG_ROOT)
				.check(importedClasses);
		});
	}

	@Test
	void inbound_and_outbounds_should_not_be_public() {
		classes().that().resideInAnyPackage("..inbound..", "..outbound..")
			.should().notBePublic().check(importedClasses);
	}

	@Test
	void only_inbound_should_use_api() {
		classes().that().resideInAPackage("..core..")
			.and().haveNameMatching(".*API")
			.should().onlyBeAccessed().byClassesThat().resideInAPackage("..inbound..")
			.check(importedClasses);
	}

	@Test
	void handlers_should_only_call_spi_or_other_handlers() {
		classes().that().resideInAPackage("..core..")
			.and().haveNameMatching(".*Handler")
			.should().onlyBeAccessed().byClassesThat().resideInAPackage("..inbound..")
			.orShould().onlyBeAccessed().byClassesThat().resideInAPackage("..core..")
			.check(importedClasses);
	}

	@ParameterizedTest
	@ValueSource(strings = {"fruits", "beverages", "products"})
	void only_defined_subpackages_exist(String pkg) {
		List.of("outbound", "inbound", "core").forEach(sandwichPkg -> {
			classes().that().resideInAPackage(".." + sandwichPkg + "..")
				.should().resideInAnyPackage(".." + sandwichPkg + "." + pkg);
		});
		classes().that().resideInAnyPackage("..inbound..", "..outbound..")
			.should().notBePublic().check(importedClasses);
	}

	@Nested
	class Other {
	}
}
