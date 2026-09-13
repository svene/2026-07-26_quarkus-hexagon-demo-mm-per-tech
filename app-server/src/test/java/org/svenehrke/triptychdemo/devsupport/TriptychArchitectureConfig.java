package org.svenehrke.triptychdemo.devsupport;

import io.soabase.recordbuilder.core.RecordBuilder;

import java.util.Optional;

/**
 * Configuration for {@link TriptychArchitecture}: the root package, the feature/cross segment names,
 * and the five naming suffixes. {@code @RecordBuilder} generates {@link TriptychArchitectureConfigBuilder},
 * whose {@code With} interface gives this record {@code withX(...)} copy methods, used by
 * {@link TriptychArchitecture}'s fluent setters instead of hand-written copy constructors.
 */
@RecordBuilder
public record TriptychArchitectureConfig(
	String rootPackage,
	String featureSegment,
	String crossSegment,
	String apiSuffix,
	String spiSuffix,
	String handlerSuffix,
	String receiverSuffix,
	String serviceSuffix,
	Optional<Boolean> allowEmptyShould,
	Optional<String> overriddenDescription
) implements TriptychArchitectureConfigBuilder.With {

	public static TriptychArchitectureConfig defaultsFor(String rootPackage) {
		return TriptychArchitectureConfigBuilder.builder()
			.rootPackage(rootPackage)
			.featureSegment("feature")
			.crossSegment("cross")
			.apiSuffix("API")
			.spiSuffix("SPI")
			.handlerSuffix("Handler")
			.receiverSuffix("Receiver")
			.serviceSuffix("Service")
			.allowEmptyShould(Optional.empty())
			.overriddenDescription(Optional.empty())
			.build();
	}
}
