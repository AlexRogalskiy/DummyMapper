package io.goodforgod.dummymapper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.goodforgod.dummymapper.error.ClassBuildException;
import io.goodforgod.dummymapper.marker.*;
import io.goodforgod.dummymapper.model.AnnotationMarker;
import io.goodforgod.dummymapper.util.MarkerUtils;
import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Class factory that creates Java Class from recreated java class map
 *
 * @author Anton Kurako (GoodforGod)
 * @see PsiJavaFileScanner
 * @since 5.4.2020
 */
public class ClassFactory {

    private static final Logger logger = LoggerFactory.getLogger(ClassFactory.class);

    private static final ClassPool CLASS_POOL = ClassPool.getDefault();

    // TODO create own classloader that could be GC so old classes can be unloaded from memory
    private static final Map<String, Integer> CLASS_SUFFIX_COUNTER = new HashMap<>();

    // /**
    // * Contains class cache via className and its hash for structure
    // */
    // private static final Map<String, Integer> CLASS_CACHE = new HashMap<>();

    public static Map<String, String> getMappedClasses(@NotNull Map<String, Marker> structure) {
        final Map<String, String> mapped = new HashMap<>();
        MarkerUtils.streamRawMarkers(structure)
                .map(m -> getMappedClasses(m.getStructure()))
                .forEach(mapped::putAll);

        MarkerUtils.streamArrayRawMarkers(structure)
                .map(m -> getMappedClasses(((RawMarker) m.getErasure()).getStructure()))
                .forEach(mapped::putAll);

        MarkerUtils.streamCollectionRawMarkers(structure)
                .map(m -> getMappedClasses(((RawMarker) m.getErasure()).getStructure()))
                .forEach(mapped::putAll);

        MarkerUtils.streamMapRawMarkers(structure)
                .map(m -> {
                    final Map<String, String> mapped1 = m.getKeyErasure() instanceof RawMarker
                            ? getMappedClasses(((RawMarker) m.getKeyErasure()).getStructure())
                            : new HashMap<>(1);

                    final Map<String, String> mapped2 = m.getKeyErasure() instanceof RawMarker
                            ? getMappedClasses(((RawMarker) m.getKeyErasure()).getStructure())
                            : Collections.emptyMap();

                    mapped1.putAll(mapped2);
                    return mapped1;
                })
                .forEach(mapped::putAll);

        structure.values().stream()
                .filter(m -> m instanceof TypedMarker)
                .findFirst()
                .ifPresent(m -> {
                    final String currentClassName = getPrevClassName(m);
                    mapped.put(m.getRoot(), currentClassName);
                });

        return mapped;
    }

    public static Class build(@NotNull Map<String, Marker> structure) {
        if (structure.isEmpty())
            throw new IllegalArgumentException("Scanned map for Class construction is empty!");

        try {
            final CtClass ctClass = buildInternal(structure, new HashMap<>());
            return Class.forName(ctClass.getName());
        } catch (Exception e) {
            throw new ClassBuildException(e);
        }
    }

    private static CtClass buildInternal(@NotNull Map<String, Marker> structure,
                                         @NotNull Map<String, CtClass> scanned) {
        try {
            final String className = getClassName(structure);
            final String originClassName = getOriginClassName(structure);

            // final int structureHash = structure.hashCode();
            // final Integer hash = CLASS_CACHE.computeIfAbsent(originClassName, k -> -1);
            // if (hash.equals(structureHash)) {
            // final String prevClassName = getPrevClassName(structure);
            // logger.debug("Retrieving CACHED class '{}' with generated name '{}' and structure hash '{}'",
            // originClassName, prevClassName, structureHash);
            // return CLASS_POOL.get(prevClassName);
            // }
            // logger.debug("CACHING class with name '{}' and structure hash '{}'", originClassName, structureHash);
            // CLASS_CACHE.put(originClassName, structureHash);

            final CtClass ownClass = getOrCreateCtClass(className);
            scanned.put(originClassName, ownClass);

            for (Map.Entry<String, Marker> entry : structure.entrySet()) {
                final String fieldName = entry.getKey();
                if (entry.getValue() instanceof ArrayMarker) {
                    final Marker erasure = ((ArrayMarker) entry.getValue()).getErasure();
                    final Class<?> type = getErasureType(erasure, scanned);
                    final CtField field = getArrayField(fieldName, (ArrayMarker) entry.getValue(), type, ownClass);
                    ownClass.addField(field);
                } else if (entry.getValue() instanceof CollectionMarker) {
                    final Marker erasure = ((CollectionMarker) entry.getValue()).getErasure();
                    final Class<?> type = getErasureType(erasure, scanned);
                    final CtField field = getCollectionField(fieldName, (CollectionMarker) entry.getValue(), type, ownClass);
                    ownClass.addField(field);
                } else if (entry.getValue() instanceof MapMarker) {
                    final Marker keyErasure = ((MapMarker) entry.getValue()).getKeyErasure();
                    final Marker valueErasure = ((MapMarker) entry.getValue()).getValueErasure();
                    final Class<?> keyType = getErasureType(keyErasure, scanned);
                    final Class<?> valueType = getErasureType(valueErasure, scanned);
                    final CtField field = getMapField(fieldName, (MapMarker) entry.getValue(), keyType, valueType, ownClass);
                    ownClass.addField(field);
                } else if (entry.getValue() instanceof TypedMarker) {
                    final CtField field = getTypedField(fieldName, (TypedMarker) entry.getValue(), ownClass);
                    ownClass.addField(field);
                } else if (entry.getValue() instanceof EnumMarker) {
                    final CtField field = getEnumField(fieldName, (EnumMarker) entry.getValue(), ownClass);
                    ownClass.addField(field);
                } else if (entry.getValue() instanceof RawMarker) {
                    final Map<String, Marker> innerStructure = ((RawMarker) entry.getValue()).getStructure();
                    final String innerClassName = getOriginClassName(innerStructure);
                    CtClass innerClass = scanned.get(innerClassName);
                    if (innerClass == null)
                        innerClass = buildInternal(innerStructure, scanned);

                    ownClass.addField(getClassField(fieldName, innerClass, ownClass));
                }
            }

            try {
                Class.forName(className);
                CLASS_SUFFIX_COUNTER.computeIfPresent(originClassName, (k, v) -> v + 1);
                return ownClass;
            } catch (Exception e) {
                ownClass.toClass(ObjectMapper.class.getClassLoader(), null);
                CLASS_SUFFIX_COUNTER.computeIfPresent(originClassName, (k, v) -> v + 1);
                return ownClass;
            }
        } catch (Exception e) {
            throw new ClassBuildException(e);
        }
    }

    private static Class<?> getErasureType(@NotNull Marker erasure,
                                           @NotNull Map<String, CtClass> scanned) {
        if (erasure instanceof TypedMarker) {
            return ((TypedMarker) erasure).getType();
        } else if (erasure instanceof EnumMarker) {
            return String.class;
        } else if (erasure instanceof RawMarker) {
            try {
                final Map<String, Marker> structure = ((RawMarker) erasure).getStructure();
                final String className = getOriginClassName(structure);

                CtClass internal = scanned.get(className);
                if (internal == null)
                    internal = buildInternal(structure, scanned);

                return Class.forName(internal.getName());
            } catch (ClassNotFoundException e) {
                return String.class;
            }
        } else {
            return String.class;
        }
    }

    private static CtClass getOrCreateCtClass(@NotNull String className) {
        try {
            // Clean previously used class (actually doesn't work such way)
            final CtClass ctClass = CLASS_POOL.get(className);
            ctClass.defrost();
            for (CtField field : ctClass.getFields())
                ctClass.removeField(field);

            return ctClass;
        } catch (NotFoundException ex) {
            return CLASS_POOL.makeClass(className);
        }
    }

    private static CtField getTypedField(@NotNull String fieldName,
                                         @NotNull TypedMarker marker,
                                         @NotNull CtClass owner) {
        try {
            final String src = String.format("public %s %s;", marker.getType().getName(), fieldName);
            final CtField field = CtField.make(src, owner);
            return addAnnotationInfo(field, marker);
        } catch (CannotCompileException e) {
            throw new ClassBuildException(e);
        }
    }

    private static CtField getArrayField(@NotNull String fieldName,
                                         @NotNull ArrayMarker marker,
                                         @NotNull Class<?> erasure,
                                         @NotNull CtClass owner) {
        try {
            final String src = String.format("public %s[] %s;", erasure.getName(), fieldName);
            final CtField field = CtField.make(src, owner);
            return addAnnotationInfo(field, marker);
        } catch (CannotCompileException e) {
            throw new ClassBuildException(e);
        }
    }

    private static CtField getCollectionField(@NotNull String fieldName,
                                              @NotNull CollectionMarker marker,
                                              @NotNull Class<?> erasure,
                                              @NotNull CtClass owner) {
        try {
            final String src = String.format("public %s %s;", marker.getType().getName(), fieldName);
            final SignatureAttribute.ClassType signature = new SignatureAttribute.ClassType(marker.getType().getName(),
                    new SignatureAttribute.TypeArgument[] {
                            new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(erasure.getName())) });

            final CtField field = CtField.make(src, owner);
            field.setGenericSignature(signature.encode());
            return addAnnotationInfo(field, marker);
        } catch (CannotCompileException e) {
            throw new ClassBuildException(e);
        }
    }

    private static CtField getMapField(@NotNull String fieldName,
                                       @NotNull MapMarker marker,
                                       @NotNull Class<?> keyErasure,
                                       @NotNull Class<?> valueErasure,
                                       @NotNull CtClass owner) {
        try {
            final String src = String.format("public %s %s;", marker.getType().getName(), fieldName);
            final SignatureAttribute.ClassType signature = new SignatureAttribute.ClassType(marker.getType().getName(),
                    new SignatureAttribute.TypeArgument[] {
                            new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(keyErasure.getName())),
                            new SignatureAttribute.TypeArgument(new SignatureAttribute.ClassType(valueErasure.getName()))
                    });

            final CtField field = CtField.make(src, owner);
            field.setGenericSignature(signature.encode());
            return addAnnotationInfo(field, marker);
        } catch (CannotCompileException e) {
            throw new ClassBuildException(e);
        }
    }

    private static CtField getEnumField(@NotNull String fieldName,
                                        @NotNull EnumMarker marker,
                                        @NotNull CtClass owner) {
        try {
            final String src = String.format("public java.lang.String %s;", fieldName);
            final CtField field = CtField.make(src, owner);
            return addAnnotationInfo(field, marker);
        } catch (CannotCompileException e) {
            throw new ClassBuildException(e);
        }
    }

    private static CtField addAnnotationInfo(@NotNull CtField field,
                                             @NotNull Marker marker) {
        if (marker.getAnnotations().isEmpty())
            return field;

        final FieldInfo fieldInfo = field.getFieldInfo();
        final ConstPool constPool = fieldInfo.getConstPool();

        for (AnnotationMarker annotationMarker : marker.getAnnotations()) {
            final Annotation a = new Annotation(annotationMarker.getName(), constPool);
            annotationMarker.getAttributes()
                    .forEach((name, v) -> getMember(v, constPool)
                            .ifPresent(member -> a.addMemberValue(name, member)));
            final AnnotationsAttribute attribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            attribute.setAnnotation(a);
            fieldInfo.addAttribute(attribute);
        }

        // final Annotation annotation = new Annotation(JsonProperty.class.getName(), constPool);
        // annotation.addMemberValue("required", new BooleanMemberValue(true, constPool));
        // AnnotationsAttribute attributeNew = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        // attributeNew.setAnnotation(annotation);
        // fieldInfo.addAttribute(attributeNew);

        return field;
    }

    private static Optional<MemberValue> getMember(Object v, ConstPool constPool) {
        if (v instanceof Boolean) {
            return Optional.of(new BooleanMemberValue((Boolean) v, constPool));
        } else if (v instanceof String) {
            return Optional.of(new StringMemberValue((String) v, constPool));
        } else if (v instanceof Character) {
            return Optional.of(new CharMemberValue((Character) v, constPool));
        } else if (v instanceof Byte) {
            return Optional.of(new ByteMemberValue((Byte) v, constPool));
        } else if (v instanceof Short) {
            return Optional.of(new ShortMemberValue((Short) v, constPool));
        } else if (v instanceof Integer) {
            return Optional.of(new IntegerMemberValue((Integer) v, constPool));
        } else if (v instanceof Long) {
            return Optional.of(new LongMemberValue((Long) v, constPool));
        } else if (v instanceof Float) {
            return Optional.of(new FloatMemberValue((Float) v, constPool));
        } else if (v instanceof Double) {
            return Optional.of(new DoubleMemberValue((Double) v, constPool));
        } else if (v instanceof Collection) {
            final MemberValue[] values = ((Collection<?>) v).stream()
                    .map(r -> getMember(r, constPool))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toArray(MemberValue[]::new);

            if (values.length > 0) {
                final ArrayMemberValue value = new ArrayMemberValue(values[0], constPool);
                value.setValue(values);
                return Optional.of(value);
            }
        }

        return Optional.empty();
    }

    private static CtField getClassField(@NotNull String fieldName,
                                         @NotNull CtClass fieldClass,
                                         @NotNull CtClass owner) {
        try {
            final String src = String.format("public %s %s;", fieldClass.getName(), fieldName);
            return CtField.make(src, owner);
        } catch (CannotCompileException e) {
            throw new ClassBuildException(e);
        }
    }

    private static String getClassName(@NotNull Map<?, ?> structure) {
        return structure.values().stream()
                .filter(v -> v instanceof TypedMarker)
                .map(m -> getClassName((Marker) m))
                .findFirst()
                .orElseThrow(() -> new ClassBuildException("Can not find Class Name while construction!"));
    }

    private static String getClassName(@NotNull Marker marker) {
        final String name = getClassNameFromPackage(marker.getRoot());
        return getClassNameWithSuffix(name);
    }

    private static String getOriginClassNameFull(@NotNull Map<?, ?> structure) {
        return structure.values().stream()
                .filter(v -> v instanceof TypedMarker)
                .map(m -> ((TypedMarker) m).getRoot())
                .findFirst()
                .orElseThrow(() -> new ClassBuildException("Can not find origin Class Name Full while construction!"));
    }

    private static String getOriginClassName(@NotNull Map<?, ?> structure) {
        return structure.values().stream()
                .filter(v -> v instanceof TypedMarker)
                .map(m -> getClassNameFromPackage(((TypedMarker) m).getRoot()))
                .findFirst()
                .orElseThrow(() -> new ClassBuildException("Can not find origin Class Name while construction!"));
    }

    private static String getPrevClassName(@NotNull Marker marker) {
        final String originClassName = getClassNameFromPackage(marker.getRoot());
        final int num = CLASS_SUFFIX_COUNTER.computeIfAbsent(originClassName, k -> 1) - 1;
        return originClassName + "_" + num;
    }

    private static String getClassNameWithSuffix(@NotNull String name) {
        return name + "_" + CLASS_SUFFIX_COUNTER.computeIfAbsent(name, k -> 0);
    }

    private static String getClassNameFromPackage(@NotNull String source) {
        final int lastIndexOf = source.lastIndexOf('.', source.length() - 6);
        return source.substring(lastIndexOf + 1, source.length() - 5);
    }
}
