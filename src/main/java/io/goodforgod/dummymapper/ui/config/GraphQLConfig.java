package io.goodforgod.dummymapper.ui.config;

import io.goodforgod.dummymapper.ui.component.CheckBoxComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

/**
 * GraphQL Mapper config
 *
 * @author Anton Kurako (GoodforGod)
 * @since 14.6.2020
 */
public class GraphQLConfig extends AbstractConfig {

    private static final String QUERY_BY_DEFAULT = "Query By Default";
    private static final String NON_NULL_BY_DEFAULT = "Non Null By Default";

    @Override
    public @NotNull Collection<JComponent> getComponents() {
        return Arrays.asList(
                new CheckBoxComponent(QUERY_BY_DEFAULT, false).build(this),
                new CheckBoxComponent(NON_NULL_BY_DEFAULT, false).build(this));
    }

    public boolean isQueryByDefault() {
        return Boolean.parseBoolean(config.getOrDefault(QUERY_BY_DEFAULT, "false"));
    }

    public boolean isQueryNonNullByDefault() {
        return Boolean.parseBoolean(config.getOrDefault(NON_NULL_BY_DEFAULT, "false"));
    }
}
