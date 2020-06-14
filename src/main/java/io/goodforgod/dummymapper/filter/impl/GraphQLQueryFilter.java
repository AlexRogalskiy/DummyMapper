package io.goodforgod.dummymapper.filter.impl;

import io.goodforgod.dummymapper.marker.RawMarker;
import io.goodforgod.dummymapper.model.AnnotationMarkerBuilder;
import io.leangen.graphql.annotations.GraphQLNonNull;
import io.leangen.graphql.annotations.GraphQLQuery;
import org.jetbrains.annotations.NotNull;

/**
 * Add {@link GraphQLNonNull} if such annotation is not present on field
 * and field is annotated as {@link GraphQLQuery}
 *
 * @author Anton Kurako (GoodforGod)
 * @since 14.6.2020
 */
public class GraphQLQueryFilter extends BaseFilter {

    @NotNull
    @Override
    public RawMarker filter(@NotNull RawMarker marker) {
        marker.getStructure().entrySet().stream()
                .filter(e -> e.getValue().getAnnotations().stream()
                        .noneMatch(a -> a.named(GraphQLQuery.class)))
                .forEach(e -> e.getValue().addAnnotation(AnnotationMarkerBuilder.get()
                        .ofField()
                        .withName(GraphQLQuery.class)
                        .build()));

        return filterRecursive(marker);
    }
}
