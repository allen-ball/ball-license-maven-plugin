package ball.maven.plugins.license;

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

    /** @serial */ @Inject private LicenseMap licenseMap = null;

    /**
     * Sole constructor.
     */
    public AnyLicenseInfoFactory() {
        super(COMPARATOR);

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
