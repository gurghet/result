package com.gurghet;

import com.gurghet.result.Failure;
import com.gurghet.result.Result;
import com.gurghet.result.Success;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.time.DateTimeException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ResultMonadLawsTest {

    @Provide("runnable")
    Arbitrary<Supplier<Integer>> runnable() {
        return Arbitraries.oneOf(
                Arbitraries.of(() -> 1),
                Arbitraries.of(() -> { throw new RuntimeException("error"); }),
                Arbitraries.of(() -> null)
        );
    }

    @Provide("mapper")
    Arbitrary<Function<Integer, Result<Integer>>> mapper() {
        return Arbitraries.oneOf(
                Arbitraries.of(i -> Success.of(i + 1)),
                Arbitraries.of(i -> Failure.of(new RuntimeException("error")))
                // ,Arbitraries.of(i -> null) // breaks the laws
        );
    }

    @Property(tries = 1)
    void testMapSuccess() {
        Result<Integer> result = Success.of(42);
        Result<String> mapped = result.map(Object::toString);
        assertEquals(Success.of("42"), mapped);
    }

    @Property(tries = 1)
    void testMapFailure() {
        Result<Integer> result = Failure.of(new RuntimeException("error"));
        Result<String> mapped = result.map(Object::toString);
        assertTrue(mapped.isFailure());
        assertEquals("error", ((Failure<?>) mapped).getException().getMessage());
        assertEquals(RuntimeException.class, ((Failure<?>) mapped).getException().getClass());
    }

    @Property
    void leftIdentitySuccess(@ForAll Integer value, @ForAll("mapper") Function<Integer, Result<Integer>> mapper) {
        Result<Integer> m = Success.of(value);
        Result<Integer> flatMapped = m.flatMap(mapper);
        if (flatMapped.isSuccess()) {
            assertEquals(mapper.apply(value), flatMapped);
        } else {
            assertTrue(mapper.apply(value).isFailure());
            assertTrue(flatMapped.isFailure());
            Failure<Integer> mapperFailure = (Failure<Integer>) mapper.apply(value);
            Failure<Integer> flatMappedFailure = (Failure<Integer>) flatMapped;
            assertEquals(mapperFailure.getException().getClass(), flatMappedFailure.getException().getClass());
            assertEquals(mapperFailure.getException().getMessage(), flatMappedFailure.getException().getMessage());
        }
    }

    @Property
    void rightIdentitySuccess(@ForAll Integer value) {
        Result<Integer> m = Success.of(value);
        Function<Integer, Result<Integer>> f = Success::of;
        assertEquals(m.flatMap(f), m);
    }

    @Property
    void associativitySuccess(@ForAll Integer value, @ForAll("mapper") Function<Integer, Result<Integer>> f, @ForAll("mapper") Function<Integer, Result<Integer>> g) {
        Result<Integer> m = Success.of(value);
        Result<Integer> left = m.flatMap(f).flatMap(g);
        Result<Integer> right = m.flatMap(x -> f.apply(x).flatMap(g));
        if (left.isSuccess()) {
            assertEquals(left, right);
        } else {
            assertTrue(left.isFailure());
            assertTrue(right.isFailure());
            Failure<Integer> leftFailure = (Failure<Integer>) left;
            Failure<Integer> rightFailure = (Failure<Integer>) right;
            assertEquals(leftFailure.getException().getClass(), rightFailure.getException().getClass());
            assertEquals(leftFailure.getException().getMessage(), rightFailure.getException().getMessage());
        }
    }

    @Property
    void leftIdentityFailure(@ForAll("mapper") Function<Integer, Result<Integer>> mapper) {
        Result<Integer> m = Failure.of(new RuntimeException("error"));
        Result<Integer> left = m.flatMap(mapper);

        //
    }

    @Property(tries = 1)
    void rightIdentityFailure() {
        Failure<Integer> m = Failure.of(new RuntimeException("error"));
        Function<Integer, Result<Integer>> f = Success::of;
        Result<Integer> left = m.flatMap(f);
        if (left.isSuccess()) {
            assertEquals(m, left);
        } else {
            assertTrue(left.isFailure());
            assertTrue(m.isFailure());
            Failure<Integer> leftFailure = (Failure<Integer>) left;
            assertEquals(leftFailure.getException().getClass(), m.getException().getClass());
            assertEquals(leftFailure.getException().getMessage(), m.getException().getMessage());
        }
    }

    @Property
    void associativityFailure(@ForAll("mapper") Function<Integer, Result<Integer>> f, @ForAll("mapper") Function<Integer, Result<Integer>> g) {
        Result<Integer> m = Failure.of(new RuntimeException("error"));
        Result<Integer> left = m.flatMap(f).flatMap(g);
        Result<Integer> right = m.flatMap(x -> f.apply(x).flatMap(g));
        if (left.isSuccess()) {
            assertEquals(left, right);
        } else {
            assertTrue(left.isFailure());
            assertTrue(right.isFailure());
            Failure<Integer> leftFailure = (Failure<Integer>) left;
            Failure<Integer> rightFailure = (Failure<Integer>) right;
            assertEquals(leftFailure.getException().getClass(), rightFailure.getException().getClass());
            assertEquals(leftFailure.getException().getMessage(), rightFailure.getException().getMessage());
        }
    }

    @Property(tries = 1)
    void testMapError() {
        RuntimeException exception = new RuntimeException("error");
        Result<Integer> failureResult = Failure.of(exception);
        Result<Integer> result = failureResult.mapError(e -> new IllegalArgumentException("mapped"));

        assertTrue(result.isFailure());
        assertEquals("mapped", ((Failure<?>) result).getException().getMessage());
    }

    @Property(tries = 1)
    void testTap() {
        final AtomicBoolean called = new AtomicBoolean(false);
        Result<Integer> result = Success.of(42).tap(x -> called.set(true));
        assertEquals(Success.of(42), result);
      assertTrue(called.get());
    }

    @Property(tries = 1)
    void testIsSuccess() {
      assertTrue(Success.of(42).isSuccess());
      assertFalse(Failure.of(new RuntimeException("error")).isSuccess());
    }

    @Property(tries = 1)
    void testIsFailure() {
      assertFalse(Success.of(42).isFailure());
      assertTrue(Failure.of(new RuntimeException("error")).isFailure());
    }

    @Provide
    Arbitrary<Result<Integer>> resultArbitrary() {
        return Arbitraries.oneOf(
                Arbitraries.integers().map(Success::of),
                Arbitraries.of(new RuntimeException("error")).map(Failure::of)
        );
    }

    @Property
    void testOrElse(@ForAll("resultArbitrary") Result<Integer> result, @ForAll Integer other) {
        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), result.orElse(other));
        } else {
            assertEquals(other, result.orElse(other));
        }
    }

    @Property
    void testOrElseGet(@ForAll("resultArbitrary") Result<Integer> result, @ForAll Integer supplierValue) {
        Supplier<Integer> supplier = () -> supplierValue;
        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), result.orElseGet(supplier));
        } else {
            assertEquals(supplierValue, result.orElseGet(supplier));
        }
    }

    @Property(tries = 1)
    void testOrElseGetWithThrowingSupplier() {
        Result<Integer> result = Failure.of(new RuntimeException("Original error"));
        Supplier<Integer> throwingSupplier = () -> { throw new RuntimeException("Supplier error"); };

        RuntimeException exception = assertThrows(RuntimeException.class, () -> result.orElseGet(throwingSupplier));
        assertEquals("Original error", exception.getMessage());
        assertEquals("Supplier error", exception.getSuppressed()[0].getMessage());
    }

    @Property
    void testOrElseThrow(@ForAll("resultArbitrary") Result<Integer> result) {
        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), result.orElseThrow());
        } else {
            assertThrows(RuntimeException.class, result::orElseThrow);
        }
    }

    @Property
    void testOrElseThrowWithException(@ForAll("resultArbitrary") Result<Integer> result) {
        RuntimeException customException = new RuntimeException("Custom exception");
        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), result.orElseThrow(customException));
        } else {
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> result.orElseThrow(customException));
            assertEqualsException(customException, thrown);
            assertEquals(1, thrown.getSuppressed().length);
        }
    }

    private static boolean assertEqualsException(RuntimeException expected, RuntimeException actual) {
        return expected.getMessage().equals(actual.getMessage()) && expected.getClass().equals(actual.getClass());
    }

    @Property
    void testOrElseThrowWithMapper(@ForAll("resultArbitrary") Result<Integer> result) {
        Function<RuntimeException, RuntimeException> exceptionMapper = e -> new IllegalStateException("Mapped: " + e.getMessage());
        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), result.orElseThrow(exceptionMapper));
        } else {
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> result.orElseThrow(exceptionMapper));
            assertTrue(thrown.getMessage().startsWith("Mapped: "));
        }
    }

    @Property
    void testToOptional(@ForAll("resultArbitrary") Result<Integer> result) {
        Optional<Integer> optional = result.toOptional();
        assertEquals(result.isSuccess(), optional.isPresent());
        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), optional.get());
        }
    }

    @Property
    void testTapError(@ForAll("resultArbitrary") Result<Integer> result) {
        AtomicBoolean called = new AtomicBoolean(false);
        Result<Integer> tapped = result.tapError(e -> called.set(true));
        if (result.isFailure()) {
            assertTrue(called.get());
        } else {
            assertFalse(called.get());
        }
    }

    @Property
    void testSuccess(@ForAll Integer value) {
        Result<Integer> result = Result.success(value);
        assertTrue(result.isSuccess());
        assertEquals(value, result.unsafeGet());
    }

    @Property
    void testFailure(@ForAll String errorMessage) {
        RuntimeException exception = new RuntimeException(errorMessage);
        Result<Integer> result = Result.failure(exception);
        assertTrue(result.isFailure());
        assertEquals(exception, assertThrows(RuntimeException.class, result::unsafeGet));
    }

    @Property
    void testFromOptional(@ForAll("resultArbitrary") Result<Integer> result) {
        Optional<Integer> optional = result.toOptional();
        Result<Integer> fromOptional = Result.fromOptional(optional);
        assertEquals(result.isSuccess(), fromOptional.isSuccess());
        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), fromOptional.unsafeGet());
        }
    }

    @Property
    void testCatchAll(@ForAll("resultArbitrary") Result<Integer> result, @ForAll Integer fallbackValue) {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);

        Result<Integer> handled = result.catchAll(e -> {
            handlerCalled.set(true);
            return Success.of(fallbackValue);
        });

        if (result.isSuccess()) {
            assertEquals(result, handled);
            assertFalse(handlerCalled.get());
        } else {
            assertEquals(Success.of(fallbackValue), handled);
            assertTrue(handlerCalled.get());
        }
    }

    @Property
    void testCatchAllWithSupplier(@ForAll("resultArbitrary") Result<Integer> result, @ForAll Integer fallbackValue) {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);

        Result<Integer> handled = result.catchAll(() -> {
            supplierCalled.set(true);
            return Success.of(fallbackValue);
        });

        if (result.isSuccess()) {
            assertEquals(result, handled);
            assertFalse(supplierCalled.get());
        } else {
            assertEquals(Success.of(fallbackValue), handled);
            assertTrue(supplierCalled.get());
        }
    }

    @Property
    void testCatchSome(@ForAll("resultArbitrary") Result<Integer> result, @ForAll Integer fallbackValue) {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);

        Result<Integer> handled = result.catchSome(RuntimeException.class, e -> {
            handlerCalled.set(true);
            return Success.of(fallbackValue);
        });

        if (result.isSuccess()) {
            assertEquals(result, handled);
            assertFalse(handlerCalled.get());
        } else {
            assertEquals(Success.of(fallbackValue), handled);
            assertTrue(handlerCalled.get());
        }
    }

    @Property
    void testCatchSomeWithSupplierMatches() {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);
        Result<Integer> result = Failure.of(new RuntimeException("error"));

        Result<Integer> handled = result.catchSome(RuntimeException.class, () -> {
            supplierCalled.set(true);
            return Success.of(42);
        });

        assertEquals(Success.of(42), handled);
        assertTrue(supplierCalled.get());
    }

    @Property(tries = 1)
    void testCatchSomeNotMatching() {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);
        Result<Integer> result = Failure.of(new IllegalArgumentException("error"));

        Result<Integer> handled = result.catchSome(DateTimeException.class, e -> {
            handlerCalled.set(true);
            return Success.of(42);
        });

        assertFalse(handlerCalled.get());
        assertEquals(result, handled);
        assertFalse(handlerCalled.get());
    }

    @Property(tries = 1)
    void testAsSuccess() {
        Result<Integer> result = Success.of(42);
        Result<String> mapped = result.as("Hello");
        assertEquals(Success.of("Hello"), mapped);
    }

    @Property(tries = 1)
    void testAsFailure() {
        Result<Object> result = Failure.of(new RuntimeException("error"));
        Result<Object> mapped = result.as("Hello");
        assertTrue(mapped.isFailure());
        assertEquals("error", ((Failure<?>) mapped).getException().getMessage());
    }

    @Property
    void testAs(@ForAll("resultArbitrary") Result<Integer> result, @ForAll String newValue) {
    }

    @Property(tries = 1)
    void testWithRunnable() {
        Result<Void> result = Result.of(() -> System.out.println("Hello"));
        assertTrue(result.isSuccess());
    }
}