package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {
  private final HashMap<Class<?>, Supplier<?>> registry = new HashMap<>();

  static <T> List<PropertyDescriptor> findInjectableProperties(Class<T> type) {
    var beanInfo = Utils.beanInfo(type);
    var propertyDescriptors = beanInfo.getPropertyDescriptors();
    return Arrays.stream(propertyDescriptors)
        .filter(propertyDescriptor -> !propertyDescriptor.getName().equals("class"))
        .filter(propertyDescriptor -> {
          var setter = propertyDescriptor.getWriteMethod();
          if (setter == null) {
            return false;
          }
          return setter.isAnnotationPresent(Inject.class);
        }).toList();
  }

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(instance, "instance is null");
    var value = registry.putIfAbsent(type, () -> instance);
    if (value != null) {
      throw new IllegalStateException("already a configuration for " + type.getName());
    }
  }

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type, "type is null");
    var supplier = registry.get(type);
    if (supplier == null) {
      throw new IllegalStateException("no configuration for " + type.getName());
    }
    return type.cast(supplier.get());
  }

  public <T> void registerProvider(Class<T> type, Supplier<T> supplier) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(supplier);
    var value = registry.putIfAbsent(type, supplier);
    if (value != null) {
      throw new IllegalStateException("already a configuration for " + type.getName());
    }
  }

  private Constructor<?> findInjectableConstructor(Class<?> type) {
    var constructors = Arrays.stream(type.getConstructors())
        .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
        .toList();
    return switch (constructors.size()) {
      case 0 -> Utils.defaultConstructor(type);
      case 1 -> constructors.getFirst();
      default -> throw new IllegalStateException();
    };
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> implementation) {
    Objects.requireNonNull(type, "type is null");
    Objects.requireNonNull(implementation, "implementation is null");
    var constructor = findInjectableConstructor(implementation);
    var properties = findInjectableProperties(implementation);
    registerProvider(type, () -> {
      var args = Arrays.stream(constructor.getParameterTypes())
          .map(this::lookupInstance)
          .toArray();
      var instance = Utils.newInstance(constructor, args);
      for (var property : properties) {
        var propertyType = property.getPropertyType();
        var value = lookupInstance(propertyType);
        Utils.invokeMethod(instance, property.getWriteMethod(), value);
      }
      return implementation.cast(instance);
    });
  }
}