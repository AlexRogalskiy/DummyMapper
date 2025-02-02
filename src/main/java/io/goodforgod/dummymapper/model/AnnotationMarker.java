package io.goodforgod.dummymapper.model;

import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * Annotation marker
 *
 * @author Anton Kurako (GoodforGod)
 * @since 1.5.2020
 */
public class AnnotationMarker {

    private final String name;
    private final boolean isInternal;
    private final boolean isFieldMarked;
    private final boolean isGetterMarked;
    private final boolean isSetterMarked;
    private final Map<String, Object> attributes;

    protected AnnotationMarker(@NotNull String name,
                               boolean isInternal,
                               boolean isFieldMarked,
                               boolean isGetterMarked,
                               boolean isSetterMarked,
                               @NotNull Map<String, Object> attributes) {
        this.isInternal = isInternal;
        this.name = name;
        this.isFieldMarked = isFieldMarked;
        this.isGetterMarked = isGetterMarked;
        this.isSetterMarked = isSetterMarked;
        this.attributes = attributes;
    }

    public @NotNull String getName() {
        return name;
    }

    public boolean isFieldMarked() {
        return isFieldMarked;
    }

    public boolean isGetterMarked() {
        return isGetterMarked;
    }

    public boolean isSetterMarked() {
        return isSetterMarked;
    }

    public boolean isInternal() {
        return isInternal;
    }

    public @NotNull Map<String, Object> getAttributes() {
        return attributes;
    }

    public boolean haveAttribute(String key) {
        return attributes.containsKey(key);
    }

    public boolean named(@NotNull Class<?> annotation) {
        return named(annotation.getName());
    }

    public boolean named(@NotNull String annotation) {
        return name.equals(annotation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AnnotationMarker that = (AnnotationMarker) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
