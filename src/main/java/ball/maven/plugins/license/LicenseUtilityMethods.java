package ball.maven.plugins.license;

/*-
 * ##########################################################################
 * License Maven Plugin
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2020 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.LicenseSet;
import org.spdx.rdfparser.license.OrLaterOperator;
import org.spdx.rdfparser.license.WithExceptionOperator;

import static lombok.AccessLevel.PRIVATE;

/**
 * {@link AnyLicenseInfo} utility methods.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@NoArgsConstructor(access = PRIVATE) @ToString
public abstract class LicenseUtilityMethods {

    /**
     * Static method to create a {@link Stream} to walk the tree rooted at
     * {@code root}.
     *
     * @param   root            The {@link AnyLicenseInfo} representing the
     *                          root of the tree.
     *
     * @return  A {@link Stream}.
     */
    public static Stream<AnyLicenseInfo> walk(AnyLicenseInfo root) {
        return walk(root, LicenseUtilityMethods::childrenOf);
    }

    /**
     * Static method to get the children of a {@link AnyLicenseInfo}.
     *
     * @param   node            The {@link AnyLicenseInfo}.
     *
     * @return  A {@link Collection} of {@link AnyLicenseInfo} for a
     *          {@link LicenseSet}, {@link OrLaterOperator}, or
     *          {@link WithExceptionOperator} node; an empty
     *          {@link Collection} otherwise.
     */
    public static Collection<AnyLicenseInfo> childrenOf(AnyLicenseInfo node) {
        AnyLicenseInfo[] children = null;

        if (node instanceof LicenseSet) {
            children = ((LicenseSet) node).getMembers();
        } else if (node instanceof OrLaterOperator) {
            children =
                new AnyLicenseInfo[] {
                    ((OrLaterOperator) node).getLicense()
                };
        } else if (node instanceof WithExceptionOperator) {
            children =
                new AnyLicenseInfo[] {
                    ((WithExceptionOperator) node).getLicense()
                };
        } else {
            children = new AnyLicenseInfo[] { };
        }

        return Arrays.asList(children);
    }

    /**
     * Returns the count of licenses represented by this
     * {@link AnyLicenseInfo}: The number of members in a
     * {@link LicenseSet} or {@code 1}.
     *
     * @param   license         The {@link AnyLicenseInfo}.
     *
     * @return  The license count.
     */
    public static int countOf(AnyLicenseInfo license) {
        int count = 0;

        if (license instanceof LicenseSet) {
            count =
                Stream.of(((LicenseSet) license).getMembers())
                .mapToInt(t -> countOf(t))
                .sum();
        } else if (license != null) {
            count = 1;
        }

        return count;
    }

    /**
     * Method to determine if an {@link AnyLicenseInfo} is "empty" (defined
     * as the license consists solely of empty containers).
     *
     * @param   license         The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if the {@code license} is empty; {@code false}
     *          otherwise.
     */
    public static boolean isEmpty(AnyLicenseInfo license) {
        return countOf(license) == 0;
    }

    /**
     * Static method to test if an {@link AnyLicenseInfo} is a branch.
     *
     * @param   node            The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if a branch; {@code false} otherwise.
     */
    public static boolean isBranch(AnyLicenseInfo node) {
        return (isLicenseSet(node) || isOperator(node));
    }

    /**
     * Static method to test if an {@link AnyLicenseInfo} is a leaf.
     *
     * @param   node            The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if a leaf; {@code false} otherwise.
     */
    public static boolean isLeaf(AnyLicenseInfo node) {
        return (! isBranch(node));
    }

    /**
     * Static method to test if an {@link AnyLicenseInfo}
     * is a {@link LicenseSet}.
     *
     * @param   node            The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if a {@code node} is a {@link LicenseSet};
     *          {@code false} otherwise.
     */
    public static boolean isLicenseSet(AnyLicenseInfo node) {
        return (node instanceof LicenseSet);
    }

    /**
     * Static method to test if an {@link AnyLicenseInfo} is an operator
     * node ({@link OrLaterOperator} or {@link WithExceptionOperator}).
     *
     * @param   node            The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if an operator node; {@code false} otherwise.
     */
    public static boolean isOperator(AnyLicenseInfo node) {
        return (node instanceof OrLaterOperator
                || node instanceof WithExceptionOperator);
    }

    /**
     * Method to test if an {@link AnyLicenseInfo} is fully specified by
     * SPDX license(s).
     *
     * @param   license         The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if fully specified; {@code false} otherwise.
     */
    public static boolean isFullySpdxListed(AnyLicenseInfo license) {
        boolean fullySpecified =
             LicenseUtilityMethods.walk(license)
            .filter(LicenseUtilityMethods::isLeaf)
            .map(t -> (t instanceof License))
            .reduce(Boolean::logicalAnd).orElse(false);

        return fullySpecified;
    }

    /**
     * Method to test if an {@link AnyLicenseInfo} is partially specified by
     * SPDX license(s).
     *
     * @param   license         The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if partially specified; {@code false}
     *          otherwise.
     */
    public static boolean isPartiallySpdxListed(AnyLicenseInfo license) {
        boolean partiallySpecified =
            LicenseUtilityMethods.walk(license)
            .filter(LicenseUtilityMethods::isLeaf)
            .map(t -> (t instanceof License))
            .reduce(Boolean::logicalOr).orElse(false);

        return partiallySpecified;
    }

    private static <T> Stream<T> walk(T root,
                                      Function<? super T,Collection<? extends T>> childrenOf) {
        Walker<T> walker =
            new Walker<>(Collections.singleton(root), childrenOf);

        return StreamSupport.stream(walker, false);
    }

    private static class Walker<T> extends AbstractSpliterator<T> {
        private final Stream<Supplier<Walker<T>>> stream;
        private Iterator<Supplier<Walker<T>>> iterator = null;
        private Spliterator<? extends T> spliterator = null;

        private Walker(Collection<? extends T> nodes,
                       Function<? super T,Collection<? extends T>> childrenOf) {
            super(Long.MAX_VALUE, IMMUTABLE | NONNULL);

            stream =
                nodes.stream()
                .filter(Objects::nonNull)
                .map(childrenOf)
                .filter(Objects::nonNull)
                .map(t -> (() -> new Walker<T>(t, childrenOf)));
            spliterator =
                nodes.stream()
                .filter(Objects::nonNull)
                .spliterator();
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> consumer) {
            boolean accepted = false;

            if (spliterator != null) {
                accepted = spliterator.tryAdvance(consumer);
            }

            if (! accepted) {
                if (iterator == null) {
                    iterator = stream.iterator();
                }

                if (iterator.hasNext()) {
                    spliterator = iterator.next().get();
                    accepted = tryAdvance(consumer);
                }
            }

            return accepted;
        }
    }
}
