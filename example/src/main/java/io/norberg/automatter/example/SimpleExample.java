package io.norberg.automatter.example;

import java.io.IOException;

import static java.lang.System.out;

public class SimpleExample {

  public static void main(final String... args) throws IOException {
    Foobar foobar = new FoobarBuilder()
        .bar(17)
        .foo("hello world")
        .build();

    out.println("bar: " + foobar.bar());
    out.println("foo: " + foobar.foo());
    out.println("foobar: " + foobar);
  }
}
