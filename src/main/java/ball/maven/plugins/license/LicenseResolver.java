package ball.maven.plugins.license;

import java.util.Collection;
import java.util.Objects;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.DisjunctiveLicenseSet;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;

/**
 * {@link AnyLicenseInfo} resolver with methods to parse, look-up, and
 * normalize.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@Slf4j
public class LicenseResolver {
    /** @serial */ private final LicenseMap licenseMap;
    /** @serial */ private final TextLicenseInfoParser textLicenseInfoParser;
    /** @serial */ private final URLLicenseInfoParser urlLicenseInfoParser;

    /**
     * Sole constructor.
     *
     * @param   licenseMap      The injected {@link LicenseMap}.
     * @param   urlLicenseInfoParser
     *                          The injected {@link URLLicenseInfoParser}.
     * @param   textLicenseInfoParser
     *                          The injected {@link TextLicenseInfoParser}.
     */
    @Inject
    public LicenseResolver(LicenseMap licenseMap,
                           URLLicenseInfoParser urlLicenseInfoParser,
                           TextLicenseInfoParser textLicenseInfoParser) {
        this.licenseMap =
            Objects.requireNonNull(licenseMap);
        this.urlLicenseInfoParser =
            Objects.requireNonNull(urlLicenseInfoParser);
        this.textLicenseInfoParser =
            Objects.requireNonNull(textLicenseInfoParser);
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() { }

    /**
     * Method to parse {@link URLLicenseInfo} and
     * {@link ExtractedLicenseInfo} ({@link TextLicenseInfo}) instances.
     *
     * @param   in            The {@link AnyLicenseInfo} to resolve.
     *
     * @return  Parsed {@link AnyLicenseInfo}.
     *
     */
    public AnyLicenseInfo parse(AnyLicenseInfo in) {
        AnyLicenseInfo out = in;

        if (in instanceof URLLicenseInfo) {
            out = parse((URLLicenseInfo) in);
        } else if (in instanceof ExtractedLicenseInfo) {
            out = parse((ExtractedLicenseInfo) in);
        }

        return out;
    }

    /**
     * Method to parse {@link ExtractedLicenseInfo}
     * ({@link TextLicenseInfo}) instances.
     *
     * @param   in            The {@link ExtractedLicenseInfo} to resolve.
     *
     * @return  Parsed {@link AnyLicenseInfo}.
     */
    public AnyLicenseInfo parse(ExtractedLicenseInfo in) {
        return textLicenseInfoParser.parse(this, in);
    }

    /**
     * Method to parse {@link URLLicenseInfo} instances.
     *
     * @param   in            The {@link URLLicenseInfo} to resolve.
     *
     * @return  Parsed {@link AnyLicenseInfo}.
     */
    public AnyLicenseInfo parse(URLLicenseInfo in) {
        return urlLicenseInfoParser.parse(this, in);
    }

    /**
     * Method to convert a {@link Collection} of {@link AnyLicenseInfo}
     * instances to either a {@link DisjunctiveLicenseSet} (if {@code 0} or
     * {@code 2} or more members) or a single {@link AnyLicenseInfo}.
     *
     * @param   in            The {@link Collection} of
     *                        {@link AnyLicenseInfo} to process.
     *
     * @return  An {@link AnyLicenseInfo} representing the license(s).
     */
    public AnyLicenseInfo toLicense(Collection<AnyLicenseInfo> in) {
        AnyLicenseInfo out = null;

        if (in.size() == 1) {
            out = in.iterator().next();
        } else {
            out =
                new DisjunctiveLicenseSet(in.stream()
                                          .toArray(AnyLicenseInfo[]::new));
        }

        return out;
    }

    /**
     * Method to test if an {@link AnyLicenseInfo} is fully specified by
     * SPDX license(s).
     *
     * @param   license         The {@link AnyLicenseInfo}.
     *
     * @return  {@code true} if fully specified; {@code false} otherwise.
     */
    public boolean isFullySpdxListed(AnyLicenseInfo license) {
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
    public boolean isPartiallySpdxListed(AnyLicenseInfo license) {
        boolean partiallySpecified =
            LicenseUtilityMethods.walk(license)
            .filter(LicenseUtilityMethods::isLeaf)
            .map(t -> (t instanceof License))
            .reduce(Boolean::logicalOr).orElse(false);

        return partiallySpecified;
    }
}
