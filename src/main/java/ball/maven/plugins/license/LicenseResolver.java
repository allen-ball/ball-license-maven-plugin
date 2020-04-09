package ball.maven.plugins.license;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.spdx.compare.LicenseCompareHelper;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.DisjunctiveLicenseSet;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.LicenseSet;

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
     * @param   in              The {@link AnyLicenseInfo} to resolve.
     *
     * @return  Parsed {@link AnyLicenseInfo}.
     *
     */
    public AnyLicenseInfo parse(AnyLicenseInfo in) {
        AnyLicenseInfo out = in;

        if (in instanceof LicenseSet) {
            List<AnyLicenseInfo> list =
                Arrays.asList(((LicenseSet) in).getMembers());

            for (int i = 0, n = list.size(); i < n; i += 1) {
                list.set(i, parse(list.get(i)));
            }

            out = toLicense(list);
        } else if (in instanceof URLLicenseInfo) {
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

    protected AnyLicenseInfo parse(Document document) {
        AnyLicenseInfo license = null;
        String[] ids = null;

        if (document.documentType() != null) {
            ids =
                Stream.of("body .content p", "body main", "body p", "body")
                .map(t -> document.select(t))
                .filter(Elements::hasText)
                .map(t -> t.text())
                .distinct()
                .map(t -> parseLicenseText(t))
                .filter(t -> (t != null && t.length > 0))
                .findFirst()
                .orElse(parseLicenseText(document.body().wholeText()));
        } else {
            ids = parseLicenseText(document.text());
        }

        if (ids != null && ids.length > 0) {
            AnyLicenseInfo[] members = new AnyLicenseInfo[ids.length];

            for (int i = 0; i < members.length; i += 1) {
                members[i] = parseLicenseString(ids[i]);
            }

            license = toLicense(Arrays.asList(members));
        } else {
            license =
                new ExtractedLicenseInfo(document.location(),
                                         document.wholeText());
        }

        return license;
    }

    protected AnyLicenseInfo parseLicenseString(String string) {
        AnyLicenseInfo license = null;

        try {
            license = LicenseInfoFactory.parseSPDXLicenseString(string);
        } catch (Exception exception) {
            log.error(string + ": " + exception.getMessage());
            throw new IllegalStateException(exception);
        }

        return license;
    }

    protected String[] parseLicenseText(String text) {
        String[] ids = new String[] { };

        try {
            ids = LicenseCompareHelper.matchingStandardLicenseIds(text);
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
        }

        return ids;
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
            AnyLicenseInfo[] members =
                in.stream().toArray(AnyLicenseInfo[]::new);

            Arrays.sort(members, Comparator.comparing(Objects::toString));

            out = new DisjunctiveLicenseSet(members);
        }

        return out;
    }
}
