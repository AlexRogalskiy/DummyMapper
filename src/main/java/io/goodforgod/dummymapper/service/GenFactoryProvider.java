package io.goodforgod.dummymapper.service;

import io.dummymaker.annotation.special.GenEmbedded;
import io.dummymaker.factory.impl.GenFactory;
import io.dummymaker.generator.IGenerator;
import io.dummymaker.model.GenRule;
import io.dummymaker.model.GenRules;
import io.dummymaker.util.CollectionUtils;
import io.dummymaker.util.StringUtils;
import io.goodforgod.dummymapper.marker.*;
import io.goodforgod.dummymapper.util.MarkerUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * GenFactory Provider that builds GenFactory with special generators for ENUM or other complex values
 *
 * @author Anton Kurako (GoodforGod)
 * @since 19.4.2020
 */
public class GenFactoryProvider {

    private static final Logger logger = LoggerFactory.getLogger(GenFactoryProvider.class);

    private GenFactoryProvider() {}

    /**
     * @param scanned data from JavaFileScanner
     * @return builds GenFactory based on scanned data from java file scanner
     * @see PsiJavaFileScanner
     * @see ClassFactory
     */
    public static GenFactory get(@NotNull Map<String, Marker> scanned) {
        final Map<String, String> mappedClasses = ClassFactory.getMappedClasses(scanned);
        final List<GenRule> rules = getRules(scanned, mappedClasses);
        return new GenFactory(GenRules.of(rules));
    }

    private static List<GenRule> getRules(@NotNull Map<String, Marker> structure,
                                          @NotNull Map<String, String> mappedClasses) {
        if (structure.isEmpty())
            return Collections.emptyList();

        final String mapped = structure.values().stream()
                .map(m -> mappedClasses.getOrDefault(m.getRoot(), ""))
                .filter(StringUtils::isNotEmpty)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Class scanned but is not registered by ClassFactory"));

        try {
            final GenRule rule = GenRule.auto(Class.forName(mapped), GenEmbedded.MAX);
            structure.forEach((k, v) -> {
                if (v instanceof EnumMarker) {
                    final IGenerator<String> generator = () -> CollectionUtils.random(((EnumMarker) v).getValues());
                    rule.add(generator, k);
                } else if (v instanceof CollectionMarker && ((CollectionMarker) v).getErasure() instanceof EnumMarker) {
                    final EnumMarker erasure = (EnumMarker) ((CollectionMarker) v).getErasure();
                    final int total = CollectionUtils.random(1, erasure.getValues().size());
                    final IGenerator<Collection<String>> generator = () -> IntStream.range(0, total)
                            .mapToObj(i -> erasure.getValues().get(i))
                            .collect(Collectors.toCollection(() -> Set.class.isAssignableFrom(((CollectionMarker) v).getType())
                                    ? new HashSet<>()
                                    : new ArrayList<>()));
                    rule.add(generator, k);
                } else if (v instanceof ArrayMarker && ((ArrayMarker) v).getErasure() instanceof EnumMarker) {
                    final EnumMarker erasure = (EnumMarker) ((ArrayMarker) v).getErasure();
                    final int total = CollectionUtils.random(1, erasure.getValues().size());
                    final IGenerator<String[]> generator = () -> IntStream.range(0, total)
                            .mapToObj(i -> erasure.getValues().get(i))
                            .toArray(String[]::new);
                    rule.add(generator, k);
                }
            });

            final List<GenRule> rawRules = MarkerUtils.streamRawMarkers(structure)
                    .flatMap(m -> getRules(m.getStructure(), mappedClasses).stream())
                    .collect(Collectors.toList());

            final List<GenRule> arrayRules = MarkerUtils.streamArrayRawMarkers(structure)
                    .flatMap(m -> getRules(((RawMarker) m.getErasure()).getStructure(), mappedClasses).stream())
                    .collect(Collectors.toList());

            final List<GenRule> collectionRules = MarkerUtils.streamCollectionRawMarkers(structure)
                    .flatMap(m -> getRules(((RawMarker) m.getErasure()).getStructure(), mappedClasses).stream())
                    .collect(Collectors.toList());

            final List<GenRule> mapRules = MarkerUtils.streamMapRawMarkers(structure)
                    .flatMap(m -> {
                        final Stream<GenRule> stream1 = m.getKeyErasure() instanceof RawMarker
                                ? getRules(((RawMarker) m.getKeyErasure()).getStructure(), mappedClasses).stream()
                                : Stream.empty();

                        final Stream<GenRule> stream2 = m.getValueErasure() instanceof RawMarker
                                ? getRules(((RawMarker) m.getValueErasure()).getStructure(), mappedClasses).stream()
                                : Stream.empty();

                        return Stream.concat(stream1, stream2);
                    })
                    .collect(Collectors.toList());

            return Stream.of(Collections.singletonList(rule), rawRules, arrayRules, collectionRules, mapRules)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
