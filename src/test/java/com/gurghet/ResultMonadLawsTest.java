package com.gurghet;

import com.gurghet.result.Failure;
import com.gurghet.result.Result;
import com.gurghet.result.Success;
import net.jqwik.api.*;

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

    @Property
    void leftIdentitySuccess(@ForAll Integer value, @ForAll("mapper") Function<Integer, Result<Integer>> mapper) {
        Result<Integer> m = Success.of(value);
        assertEquals(m.flatMap(mapper), mapper.apply(value));
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
        assertEquals(m.flatMap(f).flatMap(g), m.flatMap(x -> f.apply(x).flatMap(g)));
    }

    @Property
    void leftIdentityFailure(@ForAll("mapper") Function<Integer, Result<Integer>> mapper) {
        Result<Integer> m = Failure.of(new RuntimeException("error"));
        assertEquals(m.flatMap(mapper), m);
    }

    @Property
    void rightIdentityFailure() {
        Result<Integer> m = Failure.of(new RuntimeException("error"));
        Function<Integer, Result<Integer>> f = Success::of;
        assertEquals(m.flatMap(f), m);
    }

    @Property
    void associativityFailure(@ForAll("mapper") Function<Integer, Result<Integer>> f, @ForAll("mapper") Function<Integer, Result<Integer>> g) {
        Result<Integer> m = Failure.of(new RuntimeException("error"));
        assertEquals(m.flatMap(f).flatMap(g), m.flatMap(x -> f.apply(x).flatMap(g)));
    }

    @Property
    void testThatFlatMapIsLazy() {
        final AtomicBoolean called = new AtomicBoolean(false);
        Supplier<Integer> thunk = () -> {
            called.set(true);
            return 42;
        };
        Result.of(thunk).flatMap(x -> Success.of(x + 1));
      assertFalse(called.get());
    }

    @Property
    void testMapError() {
        RuntimeException exception = new RuntimeException("error");
        Result<Integer> failureResult = Failure.of(exception);
        Result<Integer> result = failureResult.mapError(e -> new IllegalArgumentException("mapped"));
        assertEquals(Failure.of(new IllegalArgumentException("mapped")), result);
    }

    @Property
    void testTap() {
        final AtomicBoolean called = new AtomicBoolean(false);
        Result<Integer> result = Success.of(42).tap(x -> called.set(true));
      assertFalse(called.get());
        assertEquals(Success.of(42), result);
      assertTrue(called.get());
    }

    @Property
    void testIsSuccess() {
      assertTrue(Success.of(42).isSuccess());
      assertFalse(Failure.of(new RuntimeException("error")).isSuccess());
    }

    @Property
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

    @Property
    void testOrElseGetWithThrowingSupplier() {
        Result<Integer> result = Failure.of(new RuntimeException("Original error"));
        Supplier<Integer> throwingSupplier = () -> { throw new RuntimeException("Supplier error"); };

        RuntimeException exception = assertThrows(RuntimeException.class, () -> result.orElseGet(throwingSupplier));
        assertEquals("Original error", exception.getMessage());
        assertEquals("Error in orElseGet supplier", exception.getSuppressed()[0].getMessage());
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
            assertEquals(customException, thrown);
            assertEquals(1, thrown.getSuppressed().length);
        }
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
        assertEquals(result, tapped);
        assertEquals(!result.isSuccess(), called.get());
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

        assertFalse(handlerCalled.get());

        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), handled.unsafeGet());
            assertFalse(handlerCalled.get());
        } else {
            assertTrue(handled.isSuccess());
            assertEquals(fallbackValue, handled.unsafeGet());
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

        assertFalse(supplierCalled.get());

        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), handled.unsafeGet());
            assertFalse(supplierCalled.get());
        } else {
            assertTrue(handled.isSuccess());
            assertEquals(fallbackValue, handled.unsafeGet());
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

        assertFalse(handlerCalled.get());

        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), handled.unsafeGet());
            assertFalse(handlerCalled.get());
        } else {
            assertTrue(handled.isSuccess());
            assertEquals(fallbackValue, handled.unsafeGet());
            assertTrue(handlerCalled.get());
        }
    }

    @Property
    void testCatchSomeWithSupplier(@ForAll("resultArbitrary") Result<Integer> result, @ForAll Integer fallbackValue) {
        AtomicBoolean supplierCalled = new AtomicBoolean(false);

        Result<Integer> handled = result.catchSome(RuntimeException.class, () -> {
            supplierCalled.set(true);
            return Success.of(fallbackValue);
        });

        assertFalse(supplierCalled.get());

        if (result.isSuccess()) {
            assertEquals(result.unsafeGet(), handled.unsafeGet());
            assertFalse(supplierCalled.get());
        } else {
            assertTrue(handled.isSuccess());
            assertEquals(fallbackValue, handled.unsafeGet());
            assertTrue(supplierCalled.get());
        }
    }

    @Property
    void testAs(@ForAll("resultArbitrary") Result<Integer> result, @ForAll String newValue) {
        Result<String> mapped = result.as(newValue);
        assertEquals(result.isSuccess(), mapped.isSuccess());
        if (result.isSuccess()) {
            assertEquals(newValue, mapped.unsafeGet());
        }
    }
}