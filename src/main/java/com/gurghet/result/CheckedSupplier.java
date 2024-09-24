package com.gurghet.result;

public interface CheckedSupplier<T> {
  T get() throws Exception;
}
