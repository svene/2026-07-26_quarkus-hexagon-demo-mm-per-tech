package com.example.hexademo.core.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Optional;

@JsonDeserialize(as = FruitDeliveryImpl.class)
public interface FruitDelivery {

	int quantity();

	static Optional<FruitDelivery> parse(int quantity) {
		return FruitDeliveryImpl.isValid(quantity)
			? Optional.of(new FruitDeliveryImpl(quantity))
			: Optional.empty();
	}
}
