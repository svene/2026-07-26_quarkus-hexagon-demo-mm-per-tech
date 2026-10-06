package org.svenehrke.triptychdemo.devsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.svenehrke.triptychdemo.devsupport.TriptychArchitecture.triptychArchitecture;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

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
	private static final Pattern INBOUND_MODULE_PATH = Pattern.compile("/inbound-[^/]+/");
	private static final String RUN_ON_VIRTUAL_THREAD = "io.smallrye.common.annotation.RunOnVirtualThread";

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
	 * Matches both ways a module's classes reach this scan: {@code .../inbound-kafka/target/classes/...} (reactor
	 * build) and {@code .../org/svenehrke/inbound-kafka/<version>/inbound-kafka-<version>.jar!/...} (local repo).
	 * external-inbound-kafka cannot match, it is excluded from the scan entirely.
	 */
	private static final DescribedPredicate<JavaClass> RESIDE_IN_INBOUND_MODULE = DescribedPredicate.describe(
		"reside in an inbound-* module",
		javaClass -> javaClass.getSource()
			.map(source -> INBOUND_MODULE_PATH.matcher(source.getUri().toString()).find())
			.orElse(false)
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

	/**
	 * Untrusted input must enter the domain via {@code XxxOrder.parse()}/{@code XxxDelivery.parse()}, never via
	 * the throwing constructor (see validation.md). Domain values are recognized by implementing their sealed
	 * {@code Parsed*} interface, so there is no maintained list of the commodity types.
	 * <p>
	 * Covers every class of an inbound-* module, not just {@code *Receiver}s, so a helper a receiver delegates
	 * to (request record, deserializer, ...) cannot slip through. Packages cannot tell modules apart (all of
	 * them share the feature/cross packages), so the module is taken from the class file's location instead.
	 */
	@Test
	void inbound_adapters_construct_domain_values_only_via_parse() {
		DescribedPredicate<JavaConstructorCall> constructsParsedDomainValue = DescribedPredicate.describe(
			"a constructor of a Parsed* domain value",
			call -> implementsParsedInterface(call.getTargetOwner())
		);
		noClasses().that(RESIDE_IN_INBOUND_MODULE)
			.should().callConstructorWhere(constructsParsedDomainValue)
			.because("untrusted input must go through parse(), see validation.md")
			.check(importedClasses);
	}

	/**
	 * Inbound adapters drive the domain through a {@code *Handler} only, never through an outbound port: a
	 * receiver injecting e.g. {@code FruitSupplierSPI} would compile (SPIs live in core, which every inbound
	 * module depends on) and CDI would hand it the outbound adapter directly, bypassing the use case. The
	 * module graph already rules out referencing a {@code *Service} class, but not its SPI - hence this rule.
	 * <p>
	 * Module-based like {@link #inbound_adapters_construct_domain_values_only_via_parse()}, so helpers of a
	 * receiver are covered too.
	 */
	@Test
	void inbound_adapters_do_not_use_spis() {
		noClasses().that(RESIDE_IN_INBOUND_MODULE)
			.should().dependOnClassesThat().haveSimpleNameEndingWith("SPI")
			.because("inbound adapters must go through a Handler, not bypass it via an outbound port")
			.check(importedClasses);
	}

	/**
	 * Every entry point that may block runs on a virtual thread, so a wait (JDBC, MongoDB, a supplier call) never holds
	 * a platform thread (see docs/architecture/virtual-threads-in-this-project.md). Without the annotation Quarkus would
	 * silently fall back to a platform worker thread. Methods returning {@code Uni}/{@code Multi} (the SSE stream) are
	 * exempt: they run on the event loop and hold no thread while waiting. So are interfaces: REST client interfaces
	 * carry {@code @POST} too, but are outbound. Names, not classes: the scan doesn't open third-party JARs.
	 */
	@Test
	void entry_points_run_on_virtual_threads() {
		List<String> entryPointAnnotations = List.of(
			"jakarta.ws.rs.GET", "jakarta.ws.rs.POST", "jakarta.ws.rs.PUT", "jakarta.ws.rs.DELETE", "jakarta.ws.rs.PATCH",
			"org.eclipse.microprofile.reactive.messaging.Incoming", "io.quarkus.scheduler.Scheduled");
		List<String> streamTypes = List.of("io.smallrye.mutiny.Uni", "io.smallrye.mutiny.Multi");
		DescribedPredicate<JavaMethod> blockingEntryPoint = DescribedPredicate.describe(
			"are REST, Kafka or scheduler entry points not returning Uni/Multi",
			method -> !method.getOwner().isInterface()
				&& entryPointAnnotations.stream().anyMatch(method::isAnnotatedWith)
				&& !streamTypes.contains(method.getRawReturnType().getName())
		);
		methods().that(blockingEntryPoint)
			.should().beAnnotatedWith(RUN_ON_VIRTUAL_THREAD)
			.orShould().beDeclaredInClassesThat().areAnnotatedWith(RUN_ON_VIRTUAL_THREAD)
			.because("a blocking entry point must not hold a platform thread while it waits")
			.check(importedClasses);
	}

	/** {@code @Blocking} means a platform worker thread; {@code @RunOnVirtualThread} replaces it. */
	@Test
	void nothing_runs_on_platform_worker_threads() {
		noMethods()
			.should().beAnnotatedWith("io.smallrye.reactive.messaging.annotations.Blocking")
			.orShould().beAnnotatedWith("io.smallrye.common.annotation.Blocking")
			.because("blocking code runs on virtual threads (@RunOnVirtualThread)")
			.check(importedClasses);
	}

	/**
	 * Core fires its async events only through {@code AsyncEvents}: a direct {@code Event.fireAsync(event)} would
	 * deliver them on CDI's default executor, a platform worker thread, where the observers would block it.
	 */
	@Test
	void async_events_are_fired_only_through_async_events() {
		noClasses().that().doNotHaveSimpleName("AsyncEvents")
			.should().callMethodWhere(DescribedPredicate.describe(
				"Event.fireAsync",
				call -> call.getTargetOwner().getName().equals("jakarta.enterprise.event.Event")
					&& call.getName().equals("fireAsync")))
			.because("AsyncEvents delivers them on the @EventExecutor (virtual threads)")
			.check(importedClasses);
	}

	private static boolean implementsParsedInterface(JavaClass javaClass) {
		return javaClass.getAllRawInterfaces().stream()
			.anyMatch(i -> i.getSimpleName().startsWith("Parsed"));
	}
}
