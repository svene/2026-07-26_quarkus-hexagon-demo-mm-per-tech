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
 * The root package, the feature/cross segment names, and the five naming suffixes are all fluently
 * configurable, defaulting to this project's own convention.
 */
public final class TriptychArchitecture implements ArchRule {

	private final String rootPackage;
	private final String featureSegment;
	private final String crossSegment;
	private final String apiSuffix;
	private final String spiSuffix;
	private final String handlerSuffix;
	private final String receiverSuffix;
	private final String serviceSuffix;
	private final Optional<Boolean> allowEmptyShould;
	private final Optional<String> overriddenDescription;

	private TriptychArchitecture(String rootPackage, String featureSegment, String crossSegment,
			String apiSuffix, String spiSuffix, String handlerSuffix, String receiverSuffix, String serviceSuffix,
			Optional<Boolean> allowEmptyShould, Optional<String> overriddenDescription) {
		this.rootPackage = rootPackage;
		this.featureSegment = featureSegment;
		this.crossSegment = crossSegment;
		this.apiSuffix = apiSuffix;
		this.spiSuffix = spiSuffix;
		this.handlerSuffix = handlerSuffix;
		this.receiverSuffix = receiverSuffix;
		this.serviceSuffix = serviceSuffix;
		this.allowEmptyShould = allowEmptyShould;
		this.overriddenDescription = overriddenDescription;
	}

	public static TriptychArchitecture triptychArchitecture(String rootPackage) {
		return new TriptychArchitecture(rootPackage, "feature", "cross",
			"API", "SPI", "Handler", "Receiver", "Service", Optional.empty(), Optional.empty());
	}

	public TriptychArchitecture featureSegment(String featureSegment) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, overriddenDescription);
	}

	public TriptychArchitecture crossSegment(String crossSegment) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, overriddenDescription);
	}

	public TriptychArchitecture apiSuffix(String apiSuffix) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, overriddenDescription);
	}

	public TriptychArchitecture spiSuffix(String spiSuffix) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, overriddenDescription);
	}

	public TriptychArchitecture handlerSuffix(String handlerSuffix) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, overriddenDescription);
	}

	public TriptychArchitecture receiverSuffix(String receiverSuffix) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, overriddenDescription);
	}

	public TriptychArchitecture serviceSuffix(String serviceSuffix) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, overriddenDescription);
	}

	private String featureBucketPackage() {
		return rootPackage + "." + featureSegment + "..";
	}

	private String featureSlicePackage() {
		return rootPackage + "." + featureSegment + ".(*)..";
	}

	private String crossBucketPackage() {
		return rootPackage + "." + crossSegment + "..";
	}

	private List<ArchRule> rules() {
		List<ArchRule> rules = List.of(
			classes().that().haveNameMatching(".*" + apiSuffix).should().beInterfaces(),
			classes().that().haveNameMatching(".*" + apiSuffix).should().bePublic(),
			classes().that().haveNameMatching(".*" + spiSuffix).should().beInterfaces(),
			classes().that().haveNameMatching(".*" + spiSuffix).should().bePublic(),
			classes().that().haveNameMatching(
					".*(" + apiSuffix + "|" + spiSuffix + "|" + handlerSuffix + "|" + receiverSuffix + "|" + serviceSuffix + ")")
				.should().resideInAnyPackage(featureBucketPackage(), crossBucketPackage()),
			slices().matching(featureSlicePackage()).should().notDependOnEachOther()
		);
		return allowEmptyShould.map(allow -> rules.stream().map(rule -> rule.allowEmptyShould(allow)).toList())
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
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, Optional.of(allowEmptyShould), overriddenDescription);
	}

	@Override
	public TriptychArchitecture as(String newDescription) {
		return new TriptychArchitecture(rootPackage, featureSegment, crossSegment,
			apiSuffix, spiSuffix, handlerSuffix, receiverSuffix, serviceSuffix, allowEmptyShould, Optional.of(newDescription));
	}

	@Override
	public String getDescription() {
		return overriddenDescription.orElseGet(() -> "Triptych architecture rooted at '" + rootPackage + "': "
			+ "no two '" + featureSegment + ".*' slices depend on each other; "
			+ "'*" + apiSuffix + "'/'*" + spiSuffix + "' are public interfaces; "
			+ "every '*" + apiSuffix + "|*" + spiSuffix + "|*" + handlerSuffix + "|*" + receiverSuffix + "|*" + serviceSuffix + "' class "
			+ "resides under '" + featureSegment + "' or '" + crossSegment + "'");
	}

	@Override
	public String toString() {
		return getDescription();
	}
}
