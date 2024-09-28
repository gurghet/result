package com.gurghet.result;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Result<T> {

  public static <T> Result<T> of(Supplier<T> thunk) {
    if (thunk == null) {
      throw new IllegalArgumentException("Result cannot hold a null thunk");
    }
    try {
      T value = thunk.get();
      return success(value);
    } catch (RuntimeException e) {
      return failure(e);
    }
  }

  public static Result<Void> of(Runnable runnableThunk) {
    if (runnableThunk == null) {
      throw new IllegalArgumentException("Result cannot hold a null thunk");
    }
    try {
      runnableThunk.run();
      return voidSuccess();
    } catch (RuntimeException e) {
      return failure(e);
    }
  }

  private static Result<Void> voidSuccess() {
    return new Success<>(null, true);
  }

  public static <T> Result<T> ofSneakyThrows(CheckedSupplier<T> checkedThunk) {
    if (checkedThunk == null) {
      throw new IllegalArgumentException("Result cannot hold a null thunk");
    }
    try {
      T value = checkedThunk.get();
      return success(value);
    } catch (RuntimeException e) {
      return failure(e);
    } catch (Exception e) {
      return failure(new RuntimeException("Thrown checked exception, wrapping in RuntimeException", e));
    }
  }

  public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
    try {
      if (this.isFailure()) {
        Failure<T> failure = (Failure<T>) this;
        throw failure.getException();
      } else {
        Success<T> success = (Success<T>) this;
        T x = success.getValue();
        Result<U> result = mapper.apply(x);
        if (result.isFailure()) {
          Failure<U> flatMapFailure = (Failure<U>) result;
          throw flatMapFailure.getException();
        } else {
          return result;
        }
      }
    } catch (RuntimeException e) {
      return failure(e);
    }
  }

  public <U> Result<U> map(Function<T, U> mapper) {
    if (this.isFailure()) {
      Failure<T> failure = (Failure<T>) this;
      return failure(failure.getException());
    } else {
      Success<T> success = (Success<T>) this;
      try {
        U value = mapper.apply(success.getValue());
        return success(value);
      } catch (RuntimeException e) {
        return failure(e);
      }
    }
  }

  public T unsafeGet() throws RuntimeException {
    if (isFailure()) {
      Failure<T> failure = (Failure<T>) this;
      throw failure.getException();
    } else {
      Success<T> success = (Success<T>) this;
      return success.getValue();
    }
  }

  public Result<T> mapError(Function<RuntimeException, RuntimeException> mapper) {
    try {
      if (this.isFailure()) {
        Failure<T> failure = (Failure<T>) this;
        RuntimeException e = failure.getException();
        RuntimeException mapped = mapper.apply(e);
        return failure(mapped);
      } else {
        return this;
      }
    } catch (RuntimeException e) {
      return failure(e);
    }
  }

  public Result<T> tap(Consumer<T> c) {
    return flatMap(x -> {
      c.accept(x);
      return Result.of(() -> x);
    })
            .mapError(e -> {
              e.addSuppressed(new RuntimeException("Error in tap"));
              return e;
            });
  }

  public boolean isFailure() {
    return this instanceof Failure;
  }

  public boolean isSuccess() {
    return !isFailure();
  }

  public T orElse(T other) {
    if (isFailure()) {
      return other;
    } else {
      return unsafeGet();
    }
  }

  public T orElseGet(Supplier<T> supplier) {
    if (isFailure()) {
      try {
        return supplier.get();
      } catch (RuntimeException e) {
        ((Failure<T>) this).getException().addSuppressed(e);
        throw ((Failure<T>) this).getException();
      }
    } else {
      return unsafeGet();
    }
  }

  public T orElseThrow() {
    if (isFailure()) {
      throw ((Failure<T>) this).getException();
    } else {
      return unsafeGet();
    }
  }

  public T orElseThrow(RuntimeException e) {
    if (isFailure()) {
      RuntimeException newException = new RuntimeException(e.getMessage(), e);
      newException.addSuppressed(((Failure<T>) this).getException());
      throw newException;
    } else {
      return unsafeGet();
    }
  }

  public T orElseThrow(Function<RuntimeException, ? extends RuntimeException> exceptionMapper) {
    if (isFailure()) {
      throw exceptionMapper.apply(((Failure<T>) this).getException());
    } else {
      return unsafeGet();
    }
  }

  public Optional<T> toOptional() {
    if (isFailure()) {
      return Optional.empty();
    } else {
      return Optional.of(unsafeGet());
    }
  }

  public Result<T> tapError(Consumer<RuntimeException> c) {
    return mapError(e -> {
      c.accept(e);
      return e;
    });
  }

  public static <T> Success<T> success(T value) {
    return new Success<>(value, false);
  }

  public static <T> Failure<T> failure(RuntimeException exception) {
    return new Failure<>(exception);
  }

  public static <T> Result<T> fromOptional(Optional<T> optional) {
    if (optional.isPresent()) {
      return success(optional.get());
    } else {
      return failure(new RuntimeException("Optional is not present"));
    }
  }

  public Result<T> catchAll(Function<RuntimeException, Result<T>> handler) {
    if (this.isFailure()) {
      Failure<T> failure = (Failure<T>) this;
      RuntimeException e = failure.getException();
      return handler.apply(e);
    } else {
      return this;
    }
  }

  public Result<T> catchAll(Supplier<Result<T>> handler) {
    if (this.isFailure()) {
      return handler.get();
    } else {
      return this;
    }
  }

  public Result<T> catchAll(Result<T> handler) {
    if (this.isFailure()) {
      return handler;
    } else {
      return this;
    }
  }

  public <E extends RuntimeException> Result<T> catchSome(Class<E> exceptionType, Function<E, Result<T>> handler) {
    if (this.isFailure()) {
      Failure<T> failure = (Failure<T>) this;
      RuntimeException e = failure.getException();
      if (exceptionType.isInstance(e)) {
        return handler.apply((E) e);
      } else {
        return this;
      }
    } else {
      return this;
    }
  }

  public <E extends RuntimeException> Result<T> catchSome(Class<E> exceptionType, Supplier<Result<T>> handler) {
    if (this.isFailure()) {
      Failure<T> failure = (Failure<T>) this;
      RuntimeException e = failure.getException();
      if (exceptionType.isInstance(e)) {
        return handler.get();
      } else {
        return this;
      }
    } else {
      return this;
    }
  }

  public <U> Result<U> as(U value) {
    return map(x -> value);
  }
}
