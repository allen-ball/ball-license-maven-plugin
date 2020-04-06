package ball.maven.plugins.license;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.SpdxNoneLicense;

import static ball.maven.plugins.license.LicenseUtilityMethods.isFullySpdxListed;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * {@link AnyLicenseInfo} factory that caches previous calculations by
 * {@link ExtractedLicenseInfo}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@Slf4j
public class TextLicenseInfoParser extends TreeMap<ExtractedLicenseInfo,AnyLicenseInfo> {
    private static final Comparator<ExtractedLicenseInfo> COMPARATOR =
        Comparator
        .comparing(ExtractedLicenseInfo::getExtractedText)
        .thenComparing(ExtractedLicenseInfo::getLicenseId);

    /**
     * Sole constructor.
     */
    @Inject
    public TextLicenseInfoParser() {
        super(COMPARATOR);

        put(new TextLicenseInfo(EMPTY, EMPTY), new SpdxNoneLicense());
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());
    }

    /**
     * Method to get {@link AnyLicenseInfo} for an observed license ID and
     * document.  The result is saved associated with the text and the same
     * result will be returned for any subsequent call with the same text.
     *
     * @param   resolver        The {@link LicenseResolver}.
     * @param   id              The observed license ID.
     * @param   document        The observed license {@link Document}.
     *
     * @return  An {@link AnyLicenseInfo}.  The return value is never
     *          {@code null} and will at a minimum be an
     *          {@link ExtractedLicenseInfo} including the parameters.
     */
    public AnyLicenseInfo parse(LicenseResolver resolver,
                                String id, Document document) {
        AnyLicenseInfo value = null;

        if (document != null) {
/*
            https://try.jsoup.org/

            value =
                Stream.of("#main .content", "main", "#main")
                .flatMap(t -> document.body().select(t).stream())
                .filter(Element::hasText)
                .map(t -> new TextLicenseInfo(id, t.wholeText()))
                .filter(t -> parse(t) != t)
                .map(t -> get(t))
                .findFirst().orElse(null);
*/
            if (value == null) {
                value =
                    parse(resolver,
                          new TextLicenseInfo(id,
                                              document.body().wholeText()));
            }
        } else {
            value = parse(resolver, new TextLicenseInfo(id, null));
        }

        return value;
    }

    /**
     * Method to get {@link AnyLicenseInfo} for an observed license ID and
     * document.  The result is saved associated with the text and the same
     * result will be returned for any subsequent call with the same text.
     *
     * @param   resolver        The {@link LicenseResolver}.
     * @param   key             The {@link ExtractedLicenseInfo}.
     *
     * @return  An {@link AnyLicenseInfo}.  The return value is never
     *          {@code null} and will at a minimum be the supplied
     *          {@code key}.
     */
    public AnyLicenseInfo parse(LicenseResolver resolver,
                                ExtractedLicenseInfo key) {
        return computeIfAbsent(key, k -> compute(resolver, k));
    }

    private AnyLicenseInfo compute(LicenseResolver resolver,
                                   ExtractedLicenseInfo key) {
        key.setExtractedText(key.getExtractedText().intern());

        AnyLicenseInfo value =
            entrySet().stream()
            .filter(t -> isNotBlank(key.getExtractedText()))
            .filter(t -> Objects.equals(t.getKey().getExtractedText(),
                                        key.getExtractedText()))
            .map(Map.Entry::getValue)
            .findFirst().orElse(null);
        AnyLicenseInfo maybe = null;

        if (value == null && isNotBlank(key.getLicenseId())) {
            try {
                value = resolver.parseLicenseString(key.getLicenseId());
            } catch (Exception exception) {
            }

            if (value != null && (! isFullySpdxListed(value))) {
                maybe = value;
                value = null;
            }
        }

        if (value == null) {
            if (isNotBlank(key.getExtractedText())) {
                String[] ids =
                    resolver.parseLicenseText(key.getExtractedText());

                if (ids.length > 0) {
                    AnyLicenseInfo[] members =
                        new AnyLicenseInfo[ids.length];

                    for (int i = 0; i < members.length; i += 1) {
                        members[i] = resolver.parseLicenseString(ids[i]);
                    }

                    value = resolver.toLicense(Arrays.asList(members));
                }
            }

            if (value == null) {
                value = maybe;
            }
        }

        return (value != null) ? value : key;
    }
}
