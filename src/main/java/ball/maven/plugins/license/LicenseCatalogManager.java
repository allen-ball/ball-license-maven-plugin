package ball.maven.plugins.license;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.TreeMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.ListedLicenses;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.spdx.compare.LicenseCompareHelper.matchingStandardLicenseIds;

/**
 * Provides interfaces to {@link LicenseInfoFactory} and caches the results.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@NoArgsConstructor @ToString @Slf4j
public class LicenseCatalogManager {
    private TreeMap<String,AnyLicenseInfo> map = new TreeMap<>();

    @PostConstruct
    public void init() {
        try {
            ListedLicenses listed = ListedLicenses.getListedLicenses();

            for (String id : listed.getSpdxListedLicenseIds()) {
                License license = listed.getListedLicenseById(id);

                for (String url : license.getSeeAlso()) {
                    map.put(url, license);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @PreDestroy
    public void destroy() {
    }

    /**
     * Method to parse a license name {@link String} into a {@link License}.
     *
     * @param   string          The {@link String} identifying the license.
     *
     * @returns The corresponding {@link License}.
     *
     * @throws  Exception       If the {@link String} does not specify a
     *                          {@link License}.
     */
    public License parseLicenseString(String string) throws Exception {
        return (License) LicenseInfoFactory.parseSPDXLicenseString(string);
    }

    /**
     * Method to parse a license URL ({@link String}) into a
     * {@link License}.
     *
     * @param   url             The URL (as a {@link String}) identifying
     *                          the license.
     *
     * @returns The corresponding {@link License}.
     *
     * @throws  Exception       If the argument URL ({@link String}) does
     *                          not specify a {@link License}.
     */
    public License parseLicenseURL(String url) {
        AnyLicenseInfo value = map.computeIfAbsent(url, k -> analyze(k));

        return (value instanceof License) ? ((License) value) : null;
    }

    private AnyLicenseInfo analyze(String key) {
        AnyLicenseInfo value = null;
        String text = EMPTY;

        try {
            URLConnection connection = new URL(key).openConnection();

            try (InputStream in = connection.getInputStream()) {
                text =
                    new BufferedReader(new InputStreamReader(in, UTF_8))
                    .lines()
                    .collect(joining(LF, EMPTY, LF));
            }
        } catch (Exception exception) {
            log.warn("Cannot read " + key, exception);
        }

        try {
            String[] ids = matchingStandardLicenseIds(text);

            value = parseLicenseString(ids[0]);
        } catch (Exception exception) {
            log.warn("Cannot find license for " + key);

            value =
                new ExtractedLicenseInfo(null, text,
                                         null, new String[] { key }, null);
        }

        return value;
    }
}
