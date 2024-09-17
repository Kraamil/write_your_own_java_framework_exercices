package com.github.forax.framework.mapper;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JSONWriter {
  private Map<Class<?>, Generator> configs = new HashMap<>();

  private static final ClassValue<Generator> BEAN_INFO_CLASS_VALUE = new ClassValue<Generator>() {
    @Override
    protected Generator computeValue(Class<?> aClass) {
      var properties = aClass.isRecord() ? recordProperties(aClass) : beanProperties(aClass);
      var generators = properties.stream()
          .map(propertyDescriptor -> {
            var getter = propertyDescriptor.getReadMethod();
            var annotation = getter.getAnnotation(JSONProperty.class);
            var name = annotation == null ? propertyDescriptor.getName() : annotation.value();
            var prefix = '"' + name + "\": ";
            return (Generator) (writer, bean) -> prefix  + writer.toJSON(Utils.invokeMethod(bean, getter));
            })
          .toList();
      return (writer, object) -> generators.stream()
          .map(generator -> generator.generate(writer, object))
          .collect(Collectors.joining(", ", "{", "}"));
    }
  };

  private interface Generator {
    String generate(JSONWriter writer, Object bean);
  }

  public String toJSON(Object o) {
    return switch (o) {
      case Boolean _, Integer _, Double _ -> ""+o;
      case String s -> "\"" + s + "\"";
      case null -> "null";
      default -> {
        var generator = configs.get(o.getClass());
        if (generator == null) {
          generator = BEAN_INFO_CLASS_VALUE.get(o.getClass());
        }
        yield generator.generate(this, o);
      }
    };
  }

  public <O> void configure(Class <? extends O> type, Function<? super O, String> lambda) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(lambda);
    if (configs.putIfAbsent(type, (_, object) -> lambda.apply(type.cast(object))) != null) {
      throw new IllegalStateException("Can't configure a class twice : " + type.getName());
    }
  }

  private static List<PropertyDescriptor> beanProperties(Class<?> type) {
    Objects.requireNonNull(type);
    return Arrays.stream(Utils.beanInfo(type).getPropertyDescriptors())
        .filter(propertyDescriptor -> !propertyDescriptor.getName().equals("class"))
        .toList();
  }

  private static List<PropertyDescriptor> recordProperties(Class <?> type) {
    Objects.requireNonNull(type);
    return Arrays.stream(type.getRecordComponents())
        .map(recordComponent -> {
          try {
            return new PropertyDescriptor(recordComponent.getName(), recordComponent.getAccessor(), null);
          } catch (IntrospectionException e) {
            throw new AssertionError(e);
          }
        }).toList();
  }
}
