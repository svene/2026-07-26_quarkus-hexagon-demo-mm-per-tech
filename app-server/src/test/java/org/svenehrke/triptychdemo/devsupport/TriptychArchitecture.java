package org.svenehrke.triptychdemo.devsupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.Priority;

import java.util.List;
import java.util.Optional;

/**
 * Asserts the "Triptych" architecture (see concepts.md): a hexagonal core sliced by <b>feature</b> (one
 * restricted package per commodity, e.g. {@code feature.fruit}) and <b>cross</b> (cross-cutting concerns
 * reachable from every feature, e.g. {@code cross.inventory}), with API/SPI/Handler/Receiver/Service
 * naming determining where a class must live. Mirrors the usage style of ArchUnit's own
 * {@link com.tngtech.archunit.library.Architectures#onionArchitecture()}.
 * <pre>
 * triptychArchitecture("org.svenehrke.triptychdemo").check(importedClasses);
 * </pre>
 * Checks two structural guarantees:
 * <ol>
 *   <li>No two feature slices depend on each other - cross-cutting classes are exempt by construction,
 *       since they never match the feature slice pattern in the first place.</li>
 *   <li>{@code *API}/{@code *SPI} are public interfaces, and every {@code *API}/{@code *SPI}/
 *       {@code *Handler}/{@code *Receiver}/{@code *Service} class resides under a feature or cross
 *       package - nothing is left outside the scheme.</li>
 * </ol>
 * The root package, the feature/cross segment names, and the five naming suffixes all come from a
 * {@link TriptychArchitectureConfig}, defaulting to this project's own convention via
 * {@link TriptychArchitectureConfig#defaultsFor(String)}; pass a customized config to
 * {@link #triptychArchitecture(TriptychArchitectureConfig)} for a non-default shape.
 */
public final class TriptychArchitecture implements ArchRule {

	private final TriptychArchitectureConfig config;

	private TriptychArchitecture(TriptychArchitectureConfig config) {
		this.config = config;
	}

	public static TriptychArchitecture triptychArchitecture(String rootPackage) {
		return triptychArchitecture(TriptychArchitectureConfig.defaultsFor(rootPackage));
	}

	/**
	 * For a non-default shape: build a {@link TriptychArchitectureConfig} (e.g. starting from
	 * {@link TriptychArchitectureConfig#defaultsFor(String)} and applying its generated
	 * {@code withX(...)} methods) and pass it here.
	 */
	public static TriptychArchitecture triptychArchitecture(TriptychArchitectureConfig config) {
		return new TriptychArchitecture(config);
	}

	private String featureBucketPackage() {
		return config.rootPackage() + "." + config.featureSegment() + "..";
	}

	private String featureSlicePackage() {
		return config.rootPackage() + "." + config.featureSegment() + ".(*)..";
	}

	private String crossBucketPackage() {
		return config.rootPackage() + "." + config.crossSegment() + "..";
	}

	private List<ArchRule> rules() {
		List<ArchRule> rules = List.of(
			classes().that().haveNameMatching(".*" + config.apiSuffix()).should().beInterfaces(),
			classes().that().haveNameMatching(".*" + config.apiSuffix()).should().bePublic(),
			classes().that().haveNameMatching(".*" + config.spiSuffix()).should().beInterfaces(),
			classes().that().haveNameMatching(".*" + config.spiSuffix()).should().bePublic(),
			classes().that().haveNameMatching(".*(" + config.apiSuffix() + "|" + config.spiSuffix() + "|"
					+ config.handlerSuffix() + "|" + config.receiverSuffix() + "|" + config.serviceSuffix() + ")")
				.should().resideInAnyPackage(featureBucketPackage(), crossBucketPackage()),
			slices().matching(featureSlicePackage()).should().notDependOnEachOther()
		);
		return config.allowEmptyShould().map(allow -> rules.stream().map(rule -> rule.allowEmptyShould(allow)).toList())
			.orElse(rules);
	}

	@Override
	public void check(JavaClasses classes) {
		Assertions.check(this, classes);
	}

	@Override
	public EvaluationResult evaluate(JavaClasses classes) {
		EvaluationResult result = new EvaluationResult(this, Priority.MEDIUM);
		for (ArchRule rule : rules()) {
			result.add(rule.evaluate(classes));
		}
		return result;
	}

	@Override
	public TriptychArchitecture because(String reason) {
		return as(getDescription() + ", because " + reason);
	}

	@Override
	public TriptychArchitecture allowEmptyShould(boolean allowEmptyShould) {
		return new TriptychArchitecture(config.withAllowEmptyShould(Optional.of(allowEmptyShould)));
	}

	@Override
	public TriptychArchitecture as(String newDescription) {
		return new TriptychArchitecture(config.withOverriddenDescription(Optional.of(newDescription)));
	}

	@Override
	public String getDescription() {
		return config.overriddenDescription().orElseGet(() -> "Triptych architecture rooted at '" + config.rootPackage() + "': "
			+ "no two '" + config.featureSegment() + ".*' slices depend on each other; "
			+ "'*" + config.apiSuffix() + "'/'*" + config.spiSuffix() + "' are public interfaces; "
			+ "every '*" + config.apiSuffix() + "|*" + config.spiSuffix() + "|*" + config.handlerSuffix()
			+ "|*" + config.receiverSuffix() + "|*" + config.serviceSuffix() + "' class "
			+ "resides under '" + config.featureSegment() + "' or '" + config.crossSegment() + "'");
	}

	@Override
	public String toString() {
		return getDescription();
	}
}
