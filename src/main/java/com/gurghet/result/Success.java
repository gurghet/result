package com.gurghet.result;

import java.util.Objects;

public class Success<T> extends Result<T> {
  private final T value;

  public Success(T value) {
    super(() -> value);
    if (value == null) {
      throw new IllegalArgumentException("Success cannot hold a null value");
    }
    this.value = value;
  }

  public T getValue() {
    return value;
  }

  public static <T> Success<T> of(T value) {
    return new Success<>(value);
  }

  @Override
  public String toString() {
    return "Success{" +
        "value=" + value +
        '}';
  }
}
