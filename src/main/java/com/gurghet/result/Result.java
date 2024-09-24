package com.gurghet.result;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Result<T> {
  private final CheckedSupplier<T> thunk;
  private T cachedValue;
  private boolean evaluated;

  protected Result(CheckedSupplier<T> thunk) {
    if (thunk == null) {
      throw new IllegalArgumentException("Result cannot hold a null thunk");
    }
    this.thunk = thunk;
    this.evaluated = false;
  }

  public static <T> Result<T> of(Supplier<T> thunk) {
    if (thunk == null) {
      throw new IllegalArgumentException("Result cannot hold a null thunk");
    }
    return new Result<>(() -> thunk.get());
  }

  public static <T> Result<T> ofSneakyThrows(CheckedSupplier<T> checkedThunk) {
    return new Result<>(checkedThunk);
  }

  public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
    return new Result<>(() -> {
      T value = unsafeGet();
      Result<U> result = mapper.apply(value);
      if (result == null) {
        throw new NullPointerException("Mapper returned null Result");
      }
      return result.unsafeGet();
    });
  }

  public <U> Result<U> map(Function<T, U> mapper) {
    return flatMap(x -> Result.of(() -> mapper.apply(x)));
  }

  public T unsafeGet() throws RuntimeException {
    if (!evaluated) {
      try {
        cachedValue = thunk.get();
        evaluated = true;
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException("Thrown checked exception, wrapping in RuntimeException", e);
      }
    }
    return cachedValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Result)) return false;
    Result<?> that = (Result<?>) o;

    try {
      Object thisResult = this.unsafeGet();
      Object thatResult = that.unsafeGet();

      if (thisResult == null && thatResult == null) {
        return true;
      }
      if (thisResult == null || thatResult == null) {
        return false;
      }
      return thisResult.equals(thatResult);
    } catch (RuntimeException thisEx) {
      try {
        that.unsafeGet();
        return false; // Only this threw an exception
      } catch (RuntimeException thatEx) {
        // Both threw exceptions, compare them
        return thisEx.getClass().equals(thatEx.getClass()) &&
                Objects.equals(thisEx.getMessage(), thatEx.getMessage());
      }
    }
  }

  @Override
  public int hashCode() {
    try {
      Object result = unsafeGet();
      return Objects.hashCode(result);
    } catch (RuntimeException e) {
      return Objects.hash(e.getClass(), e.getMessage());
    }
  }

  @Override
  public String toString() {
    try {
      return "Success(" + unsafeGet() + ")";
    } catch (RuntimeException e) {
      return "Failure(" + e + ")";
    }
  }

  public Result<T> mapError(Function<RuntimeException, RuntimeException> mapper) {
    return new Result<>(() -> {
      try {
        return unsafeGet();
      } catch (RuntimeException e) {
        throw mapper.apply(e);
      }
    });
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
    try {
      unsafeGet();
      return false;
    } catch (RuntimeException e) {
      return true;
    }
  }

  public boolean isSuccess() {
    return !isFailure();
  }

  public T orElse(T other) {
    try {
      return unsafeGet();
    } catch (RuntimeException e) {
      return other;
    }
  }

  public T orElseGet(Supplier<T> supplier) {
    try {
      return unsafeGet();
    } catch (RuntimeException e) {
      try {
        return supplier.get();
      } catch (RuntimeException e2) {
        e.addSuppressed(new RuntimeException("Error in orElseGet supplier", e2));
        throw e;
      }
    }
  }

  public T orElseThrow() {
    try {
      return unsafeGet();
    } catch (RuntimeException e) {
      throw e;
    }
  }

  public T orElseThrow(RuntimeException e) {
    try {
      return unsafeGet();
    } catch (RuntimeException e2) {
      e.addSuppressed(e2);
      throw e;
    }
  }

  public T orElseThrow(Function<RuntimeException, ? extends RuntimeException> exceptionMapper) {
    try {
      return unsafeGet();
    } catch (RuntimeException e) {
      throw exceptionMapper.apply(e);
    }
  }

  public Optional<T> toOptional() {
    try {
      return Optional.of(unsafeGet());
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  public Result<T> tapError(Consumer<RuntimeException> c) {
    return mapError(e -> {
      c.accept(e);
      return e;
    });
  }

  public static <T> Success<T> success(T value) {
    return new Success<>(value);
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
    return new Result<>(() -> {
      try {
        return this.unsafeGet();
      } catch (RuntimeException e) {
        return handler.apply(e).unsafeGet();
      }
    });
  }

  public Result<T> catchAll(Supplier<Result<T>> handler) {
    return new Result<>(() -> {
      try {
        return this.unsafeGet();
      } catch (RuntimeException e) {
        return handler.get().unsafeGet();
      }
    });
  }

  public Result<T> catchAll(Result<T> handler) {
    return new Result<>(() -> {
      try {
        return this.unsafeGet();
      } catch (RuntimeException e) {
        return handler.unsafeGet();
      }
    });
  }

  public <E extends RuntimeException> Result<T> catchSome(Class<E> exceptionType, Function<E, Result<T>> handler) {
    return new Result<>(() -> {
      try {
        return this.unsafeGet();
      } catch (RuntimeException e) {
        if (exceptionType.isInstance(e)) {
          return handler.apply(exceptionType.cast(e)).unsafeGet();
        } else {
          throw e;
        }
      }
    });
  }

  public <E extends RuntimeException> Result<T> catchSome(Class<E> exceptionType, Supplier<Result<T>> handler) {
    return new Result<>(() -> {
      try {
        return this.unsafeGet();
      } catch (RuntimeException e) {
        if (exceptionType.isInstance(e)) {
          return handler.get().unsafeGet();
        } else {
          throw e;
        }
      }
    });
  }

  public <U> Result<U> as(U value) {
    return map(x -> value);
  }
}
