package ball.maven.plugins.license;

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

    /** @serial */ private final LicenseMap licenseMap;

    /**
     * Sole constructor.
     *
     * @param   licenseMap      The injected {@link LicenseMap}.
     */
    @Inject
    public AnyLicenseInfoFactory(LicenseMap licenseMap) {
        super(COMPARATOR);

        this.licenseMap = Objects.requireNonNull(licenseMap);

        ExtractedLicenseInfo key = new ExtractedLicenseInfo(EMPTY, EMPTY);

        key.setName(key.getLicenseId());

        put(key, new SpdxNoneLicense());
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
            value =
                Stream.of(/* main, content, etc..., */ document.body())
                .filter(Objects::nonNull)
                .filter(Element::hasText)
                .map(Element::text)
                .map(t -> get(id, t))
                .filter(t -> (! (t instanceof ExtractedLicenseInfo)))
                .findFirst().orElse(get(id, document.text()));
        } else {
            value = get(id, (String) null);
        }

        return value;
    }

    /**
     * Method to get {@link AnyLicenseInfo} for an observed license ID and
     * text.  The result is saved associated with the text and the same
     * result will be returned for any subsequent call with the same text.
     *
     * @param   id              The observed license ID.
     * @param   text            The observed license text.
     *
     * @return  An {@link AnyLicenseInfo}.  The return value is never
     *          {@code null} and will at a minimum be an
     *          {@link ExtractedLicenseInfo} including the parameters.
     */
    public AnyLicenseInfo get(String id, String text) {
        ExtractedLicenseInfo key =
            new ExtractedLicenseInfo(isNotBlank(id) ? id : EMPTY,
                                     isNotBlank(text) ? text : EMPTY);

        key.setName(key.getLicenseId());

        return computeIfAbsent(key, k -> compute(k));
    }

    private AnyLicenseInfo compute(ExtractedLicenseInfo key) {
        key.setExtractedText(key.getExtractedText().intern());
        key.setSeeAlso(new String[] { });

        AnyLicenseInfo value = licenseMap.get(key.getLicenseId());

        if (value == null) {
            try {
                value = parseSPDXLicenseString(key.getLicenseId());
            } catch (Exception exception) {
            }
        }

        if (value == null) {
            AnyLicenseInfo previous =
                entrySet().stream()
                .filter(t -> isNotBlank(key.getExtractedText()))
                .filter(t -> Objects.equals(t.getKey().getExtractedText(),
                                            key.getExtractedText()))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);

            if (previous == null) {
                try {
                    String[] ids =
                        matchingStandardLicenseIds(key.getExtractedText());

                    value = parseSPDXLicenseString(ids[0]);
                } catch (Exception exception) {
                    value = key;
                }
            } else {
                if (! (previous instanceof ExtractedLicenseInfo)) {
                    value = previous;
                } else {
                    value = key;
                }
            }
        }

        return value;
    }
}
