package com.gurghet.result;

import java.util.Objects;

public class Success<T> extends Result<T> {
  private final T value;
  private final boolean isVoid;

  public Success(T value, boolean isVoid) {
    this.isVoid = isVoid;
    if (value == null && !isVoid) {
      throw new IllegalArgumentException("Success cannot hold a null value");
    }
    if (!isVoid) {
      this.value = value;
    } else {
      this.value = null;
    }
  }

  private Success(T value) {
    this.isVoid = false;
    if (value == null) {
      throw new IllegalArgumentException("Success cannot hold a null value");
    }
    this.value = value;
  }

  public T getValue() {
    if (isVoid) {
      return null;
    } else {
      return value;
    }
  }

  public static <T> Success<T> of(T value) {
    return new Success<>(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Success<?> success = (Success<?>) o;
    return isVoid == success.isVoid && Objects.equals(value, success.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, isVoid);
  }

  @Override
  public String toString() {
    return "Success{" +
        "value=" + value +
        '}';
  }
}
