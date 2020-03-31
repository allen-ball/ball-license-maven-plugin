package ball.maven.plugins.license;

import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators.AbstractSpliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * {@link Spliterator} implemenation to build {@link Stream}s to walk a tree
 * of {@code <T>} nodes.  See {@link #walk(Object,Function)}.
 *
 * @param       <T>             The type of node.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
public class Walker<T> extends AbstractSpliterator<T> {
    private final Stream<Supplier<Walker<T>>> stream;
    private Iterator<Supplier<Walker<T>>> iterator = null;
    private Spliterator<T> spliterator = null;

    private Walker(T node, Function<? super T,T[]> childrenOf) {
        super(Long.MAX_VALUE, Spliterator.IMMUTABLE | Spliterator.NONNULL);

        stream =
            Stream.of(node)
            .filter(Objects::nonNull)
            .map(childrenOf)
            .filter(Objects::nonNull)
            .flatMap(t -> Stream.<T>of(t))
            .map(t -> (() -> new Walker<T>(t, childrenOf)));
        spliterator =
            Stream.of(node)
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

    /**
     * Entry-point to create a {@link Stream} for traversing a tree of type
     * {@code <T>} nodes.  The caller need only to supply the root node and
     * a {@link Function} to calculate the children nodes.
     *
     * @param   root            The root node.
     * @param   childrenOf      The {@link Function} to get the children of
     *                          a node.
     * @param   <T>             The type of node.
     */
    public static <T> Stream<T> walk(T root,
                                     Function<? super T,T[]> childrenOf) {
        Walker<T> walker = new Walker<>(root, childrenOf);

        return StreamSupport.stream(walker, false);
    }
}
