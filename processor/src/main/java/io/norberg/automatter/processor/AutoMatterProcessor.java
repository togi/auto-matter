package io.norberg.automatter.processor;

import com.google.auto.service.AutoService;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.squareup.javawriter.JavaWriter;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

import io.norberg.automatter.AutoMatter;

import static java.lang.String.format;
import static javax.lang.model.SourceVersion.RELEASE_6;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * An annotation processor that takes a value type defined as an interface with getter methods and
 * materializes it, generating a concrete builder and value class.
 */
@AutoService(Processor.class)
@SupportedSourceVersion(RELEASE_6)
public final class AutoMatterProcessor extends AbstractProcessor {

  private static final String JAVA_LANG = "java.lang.";

  private Filer filer;
  private Elements elements;

  @Override
  public synchronized void init(final ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    filer = processingEnv.getFiler();
    elements = processingEnv.getElementUtils();
  }

  @Override
  public boolean process(final Set<? extends TypeElement> annotations,
                         final RoundEnvironment env) {
    final Set<? extends Element> elements = env.getElementsAnnotatedWith(AutoMatter.class);
    for (Element element : elements) {
      try {
        writeBuilder(element, env);
      } catch (IOException e) {
        processingEnv.getMessager().printMessage(ERROR, e.getMessage());
      }
    }
    return false;
  }

  private void writeBuilder(final Element element, final RoundEnvironment env) throws IOException {
    final String packageName = elements.getPackageOf(element).getQualifiedName().toString();
    final Name targetSimpleName = element.getSimpleName();
    final String targetName = fullyQualifedName(packageName, targetSimpleName.toString());
    final String builderName = targetName + "Builder";
    final String simpleBuilderName = simpleName(builderName);

    final List<ExecutableElement> fields = enumerateFields(element);

    final JavaFileObject sourceFile = filer.createSourceFile(builderName);
    final JavaWriter writer = new JavaWriter(sourceFile.openWriter());

    writer.emitPackage(packageName);
    writer.emitImports("java.util.Arrays",
//                       "com.fasterxml.jackson.annotation.JsonCreator",
//                       "com.fasterxml.jackson.annotation.JsonProperty",
                       "javax.annotation.Generated");

    writer.emitEmptyLine();
    writer.emitAnnotation(
        Generated.class,
        ImmutableMap.of("value", "\"" + AutoMatterProcessor.class.getName() + "\""));
    writer.beginType(simpleBuilderName, "class", EnumSet.of(PUBLIC, FINAL));

    emitFields(writer, fields);
    emitSetters(writer, simpleBuilderName, fields);
    emitBuild(targetSimpleName, writer, fields);
    emitValue(targetSimpleName, writer, fields);

    writer.endType();
    writer.close();
  }

  private String fullyQualifedName(final String packageName, final String simpleName) {
    return packageName.isEmpty()
           ? simpleName
           : packageName + "." + simpleName;
  }

  private void emitFields(final JavaWriter writer, final List<ExecutableElement> fields)
      throws IOException {
    writer.emitEmptyLine();
    for (ExecutableElement field : fields) {
      writer.emitField(fieldType(field), fieldName(field), EnumSet.of(PRIVATE));
    }
  }

  private void emitValue(final Name targetName, final JavaWriter writer,
                         final List<ExecutableElement> fields)
      throws IOException {
    writer.emitEmptyLine();
    writer.beginType("Value", "class", EnumSet.of(PRIVATE, STATIC, FINAL),
                     null, targetName.toString());
    emitValueFields(writer, fields);
    emitValueConstructor(writer, fields);
    emitValueGetters(writer, fields);
    emitValueEquals(writer, fields);
    emitValueHashCode(writer, fields);
    emitValueToString(writer, fields, targetName);
    writer.endType();
  }

  private void emitValueConstructor(final JavaWriter writer, final List<ExecutableElement> fields)
      throws IOException {
    writer.emitEmptyLine();
    final List<String> parameters = Lists.newArrayList();
    for (ExecutableElement field : fields) {
      parameters.add("@" + JsonProperty.class.getName() +
                     "(\"" + fieldName(field) + "\") " + fieldType(field));
      parameters.add(fieldName(field));
    }
    emitAnnotation(writer, JsonCreator.class);
    writer.beginConstructor(EnumSet.of(PRIVATE), parameters, null);
    for (ExecutableElement field : fields) {
      writer.emitStatement("this.%1$s = %1$s", fieldName(field));
    }
    writer.endConstructor();
  }

  private void emitAnnotation(final JavaWriter writer,
                              final Class<? extends Annotation> annotation) throws IOException {
    final boolean compressing = writer.isCompressingTypes();
    writer.setCompressingTypes(false);
    writer.emitAnnotation(annotation);
    writer.setCompressingTypes(compressing);
  }

  private void emitValueFields(final JavaWriter writer, final List<ExecutableElement> fields)
      throws IOException {
    writer.emitEmptyLine();
    for (ExecutableElement field : fields) {
      writer.emitField(fieldType(field), fieldName(field), EnumSet.of(PRIVATE, FINAL));
    }
  }

  private void emitValueGetters(final JavaWriter writer, final List<ExecutableElement> fields)
      throws IOException {
    for (ExecutableElement field : fields) {
      emitValueGetter(writer, field);
    }
  }

  private void emitValueGetter(final JavaWriter writer, final ExecutableElement field)
      throws IOException {
    writer.emitEmptyLine();
    emitAnnotation(writer, JsonProperty.class);
    writer.emitAnnotation(Override.class);
    writer.beginMethod(fieldType(field), fieldName(field), EnumSet.of(PUBLIC));
    writer.emitStatement("return %s", fieldName(field));
    writer.endMethod();
  }

  private void emitValueEquals(final JavaWriter writer, final List<ExecutableElement> fields)
      throws IOException {

    writer.emitEmptyLine();
    writer.emitAnnotation(Override.class);
    writer.beginMethod("boolean", "equals", EnumSet.of(PUBLIC), "Object", "o");

    writer.beginControlFlow("if (this == o)");
    writer.emitStatement("return true");
    writer.endControlFlow();

    writer.beginControlFlow("if (o == null || getClass() != o.getClass())");
    writer.emitStatement("return false");
    writer.endControlFlow();

    if (!fields.isEmpty()) {
      writer.emitEmptyLine();
      writer.emitStatement("final Value value = (Value) o");
      writer.emitEmptyLine();
      for (ExecutableElement field : fields) {
        writer.beginControlFlow(fieldNotEqualCondition(field));
        writer.emitStatement("return false");
        writer.endControlFlow();
      }
    }

    writer.emitEmptyLine();
    writer.emitStatement("return true");
    writer.endMethod();
  }

  private String fieldNotEqualCondition(final ExecutableElement field) {
    final String name = field.getSimpleName().toString();
    final TypeMirror type = field.getReturnType();
    switch (type.getKind()) {
      case LONG:
      case INT:
      case BOOLEAN:
      case BYTE:
      case SHORT:
      case CHAR:
        return format("if (%1$s != value.%1$s)", name);
      case FLOAT:
        return format("if (Float.compare(value.%1$s, %1$s) != 0)", name);
      case DOUBLE:
        return format("if (Double.compare(value.%1$s, %1$s) != 0)", name);
      case ARRAY:
        return format("if (!Arrays.equals(%1$s, value.%1$s))", name);
      case DECLARED:
        return format("if (%1$s != null ? !%1$s.equals(value.%1$s) : value.%1$s != null)", name);
      default:
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }

  private void emitValueHashCode(final JavaWriter writer, final List<ExecutableElement> fields)
      throws IOException {
    writer.emitEmptyLine();
    writer.emitAnnotation(Override.class);
    writer.beginMethod("int", "hashCode", EnumSet.of(PUBLIC));
    writer.emitStatement("int result = 0");
    writer.emitStatement("long temp");
    for (ExecutableElement field : fields) {
      final String name = field.getSimpleName().toString();
      final TypeMirror type = field.getReturnType();
      switch (type.getKind()) {
        case LONG:
          writer.emitStatement("result = 31 * result + (int) (%1$s ^ (%1$s >>> 32))", name);
          break;
        case INT:
          writer.emitStatement("result = 31 * result + %s", name);
          break;
        case BOOLEAN:
          writer.emitStatement("result = 31 * result + (%s ? 1 : 0)", name);
          break;
        case BYTE:
        case SHORT:
        case CHAR:
          writer.emitStatement("result = 31 * result + (int) %s", name);
          break;
        case FLOAT:
          writer.emitStatement("result = 31 * result + " +
                               "(%1$s != +0.0f ? Float.floatToIntBits(%1$s) : 0)", name);
          break;
        case DOUBLE:
          writer.emitStatement("temp = Double.doubleToLongBits(%s)", name);
          writer.emitStatement("result = 31 * result + (int) (temp ^ (temp >>> 32))");
          break;
        case ARRAY:
          writer.emitStatement("result = 31 * result + " +
                               "(%1$s != null ? Arrays.hashCode(%1$s) : 0)", name);
          break;
        case DECLARED:
          writer.emitStatement("result = 31 * result + (%1$s != null ? %1$s.hashCode() : 0)", name);
          break;
        default:
          throw new IllegalArgumentException("Unsupported type: " + type);
      }
    }
    writer.emitStatement("return result");
    writer.endMethod();
  }

  private void emitValueToString(final JavaWriter writer, final List<ExecutableElement> fields,
                                 final Name targetName)
      throws IOException {
    writer.emitEmptyLine();
    writer.emitAnnotation(Override.class);
    writer.beginMethod("String", "toString", EnumSet.of(PUBLIC));
    emitToStringStatement(writer, fields, targetName);
    writer.endMethod();
  }

  private void emitToStringStatement(final JavaWriter writer, final List<ExecutableElement> fields,
                                     final Name targetName) throws IOException {
    final StringBuilder builder = new StringBuilder();
    builder.append("return \"").append(targetName).append("{\" + \n");
    boolean first = true;
    for (ExecutableElement field : fields) {
      final String comma = first ? "" : ", ";
      final String name = fieldName(field);
      if (field.getReturnType().getKind() == ARRAY) {
        builder.append(format("\"%1$s%2$s=\" + Arrays.toString(%2$s) +\n", comma, name));
      } else {
        builder.append(format("\"%1$s%2$s=\" + %2$s +\n", comma, name));
      }
      first = false;
    }
    builder.append("'}'");
    writer.emitStatement(builder.toString());
  }

  private void emitBuild(final Name targetName, final JavaWriter writer,
                         final List<ExecutableElement> fields) throws IOException {
    writer.emitEmptyLine();
    writer.beginMethod(targetName.toString(), "build", EnumSet.of(PUBLIC));
    final List<String> parameters = Lists.newArrayList();
    for (Element field : fields) {
      parameters.add(field.getSimpleName().toString());
    }
    writer.emitStatement("return new Value(%s)", Joiner.on(", ").join(parameters));
    writer.endMethod();
  }

  private void emitSetters(final JavaWriter writer, final String builderName,
                           final List<ExecutableElement> fields) throws IOException {
    for (final ExecutableElement field : fields) {
      emitSetter(writer, builderName, field);
    }
  }

  private void emitSetter(final JavaWriter writer, final String builderName,
                          final ExecutableElement field)
      throws IOException {
    writer.emitEmptyLine();
    writer.beginMethod(builderName, fieldName(field), EnumSet.of(PUBLIC),
                       fieldType(field), fieldName(field));
    writer.emitStatement("this.%1$s = %1$s", fieldName(field));
    writer.emitStatement("return this");
    writer.endMethod();
  }

  private String fieldName(final ExecutableElement field) {
    return field.getSimpleName().toString();
  }

  private String fieldType(final ExecutableElement field) {
    final String name = field.getReturnType().toString();
    if (name.startsWith(JAVA_LANG)) {
      return name.substring(JAVA_LANG.length());
    } else {
      return name;
    }
  }

  private List<ExecutableElement> enumerateFields(final Element element) {
    final List<ExecutableElement> fields = Lists.newArrayList();
    for (final Element member : element.getEnclosedElements()) {
      if (member.getKind().equals(ElementKind.METHOD)) {
        final ExecutableElement executable = (ExecutableElement) member;
        if (executable.getModifiers().contains(STATIC)) {
          continue;
        }
        fields.add(executable);
      }
    }
    return fields;
  }

  private static String simpleName(String fullyQualifiedName) {
    int lastDot = fullyQualifiedName.lastIndexOf('.');
    return fullyQualifiedName.substring(lastDot + 1, fullyQualifiedName.length());
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(AutoMatter.class.getName());
  }
}
