package ball.maven.plugins.license;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
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

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.spdx.compare.LicenseCompareHelper.matchingStandardLicenseIds;
import static org.spdx.rdfparser.license.LicenseInfoFactory.parseSPDXLicenseString;

/**
 * {@link AnyLicenseInfo} factory that caches previous calculations by
 * {@link ExtractedLicenseInfo}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@Slf4j
public class AnyLicenseInfoFactory extends TreeMap<ExtractedLicenseInfo,AnyLicenseInfo> {
    private static final Comparator<ExtractedLicenseInfo> COMPARATOR =
        Comparator
        .comparing(ExtractedLicenseInfo::getLicenseId)
        .thenComparing(ExtractedLicenseInfo::getExtractedText);

    /**
     * Sole constructor.
     */
    @Inject
    public AnyLicenseInfoFactory() {
        super(COMPARATOR);

        put(key(EMPTY, EMPTY), new SpdxNoneLicense());
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
     * @param   id              The observed license ID.
     * @param   document        The observed license {@link Document}.
     *
     * @return  An {@link AnyLicenseInfo}.  The return value is never
     *          {@code null} and will at a minimum be an
     *          {@link ExtractedLicenseInfo} including the parameters.
     */
    public AnyLicenseInfo get(String id, Document document) {
        AnyLicenseInfo value = null;

        if (document != null) {
/*
            value =
                Stream.of("#main .content", "main", "#main")
                .flatMap(t -> document.body().select(t).stream())
                .filter(Element::hasText)
                .map(t -> key(id, t.wholeText()))
                .filter(t -> computeIfAbsent(t, k -> compute(k)) != t)
                .map(t -> get(t))
                .findFirst().orElse(null);
*/
            if (value == null) {
                value = computeIfAbsent(id, document.body().wholeText());
            }
        } else {
            value = computeIfAbsent(id, null);
        }

        return value;
    }

    private AnyLicenseInfo computeIfAbsent(String id, String text) {
        return computeIfAbsent(key(id, text), k -> compute(k));
    }

    private AnyLicenseInfo compute(ExtractedLicenseInfo key) {
        AnyLicenseInfo value = value(key);

        return (value != null) ? value : key;
    }

    private ExtractedLicenseInfo key(String id, String text) {
        ExtractedLicenseInfo key =
            new ExtractedLicenseInfo(isNotBlank(id) ? id : EMPTY,
                                     isNotBlank(text) ? text : EMPTY);

        key.setExtractedText(key.getExtractedText().intern());
        key.setName(key.getLicenseId());
        key.setSeeAlso(new String[] { });

        return key;
    }

    private AnyLicenseInfo value(ExtractedLicenseInfo key) {
        AnyLicenseInfo value =
            entrySet().stream()
            .filter(t -> isNotBlank(key.getExtractedText()))
            .filter(t -> Objects.equals(t.getKey().getExtractedText(),
                                        key.getExtractedText()))
            .map(Map.Entry::getValue)
            .findFirst().orElse(null);

        if (value == null) {
            try {
                value = parseSPDXLicenseString(key.getLicenseId());
            } catch (Exception exception) {
            }
        }

        if (value == null) {
            try {
                String[] ids =
                    matchingStandardLicenseIds(key.getExtractedText());

                value = parseSPDXLicenseString(ids[0]);
            } catch (Exception exception) {
            }
        }

        return value;
    }
}
