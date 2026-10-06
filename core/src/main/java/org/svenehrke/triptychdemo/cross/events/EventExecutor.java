package org.svenehrke.triptychdemo.cross.events;

import jakarta.inject.Qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The {@link java.util.concurrent.Executor} that core's async events are delivered on (see {@link AsyncEvents}). Core
 * only names it; an adapter produces it - in this app Quarkus' virtual-thread executor (inbound-event).
 */
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER})
public @interface EventExecutor {}
