package com.gurghet.result;

public class Failure<T> extends Result<T> {
  private final RuntimeException exception;

  public Failure(RuntimeException exception) {
    if (exception == null) {
      throw new IllegalArgumentException("Failure cannot hold a null exception");
    }
    this.exception = exception;
  }

  public RuntimeException getException() {
    return exception;
  }

  public static <T> Failure<T> of(RuntimeException exception) {
    return new Failure<>(exception);
  }

  @Override
  public String toString() {
    return "Failure{" +
        "exception=" + exception +
        '}';
  }
}
