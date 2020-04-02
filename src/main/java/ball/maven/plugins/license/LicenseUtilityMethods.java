package ball.maven.plugins.license;

import java.util.stream.Stream;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.spdx.rdfparser.license.AnyLicenseInfo;
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
        return Walker.<AnyLicenseInfo>walk(root,
                                           LicenseUtilityMethods::childrenOf);
    }

    /**
     * Static method to get the children of a {@link AnyLicenseInfo}.
     *
     * @param   node            The {@link AnyLicenseInfo}.
     *
     * @return  A possibly non-empty {@link AnyLicenseInfo AnyLicenseInfo[]}
     *          for a {@link LicenseSet}, {@link OrLaterOperator},
     *          or {@link WithExceptionOperator} node; an empty array
     *          otherwise.
     */
    public static AnyLicenseInfo[] childrenOf(AnyLicenseInfo node) {
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

        return children;
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
}