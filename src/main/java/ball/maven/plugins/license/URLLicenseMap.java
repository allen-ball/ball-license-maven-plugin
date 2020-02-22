package ball.maven.plugins.license;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.TreeMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
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
 * {@link URL} ({@link String} representation) to {@link AnyLicenseInfo}
 * {@link java.util.Map} implementation.  The {@link #get(Object)} method
 * transparently calculates and caches any value.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@NoArgsConstructor @Slf4j
public class URLLicenseMap extends TreeMap<String,AnyLicenseInfo> {
    @PostConstruct
    public void init() {
        try {
            ListedLicenses listed = ListedLicenses.getListedLicenses();

            for (String id : listed.getSpdxListedLicenseIds()) {
                License license = listed.getListedLicenseById(id);

                for (String url : license.getSeeAlso()) {
                    put(url, license);
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @PreDestroy
    public void destroy() {
    }

    @Override
    public AnyLicenseInfo get(Object key) {
        AnyLicenseInfo value = super.get(key);

        if (value == null) {
            value = compute((String) key);

            put((String) key, value);
        }

        return value;
    }

    private AnyLicenseInfo compute(String url) {
        AnyLicenseInfo value = null;
        String text = EMPTY;

        try {
            URLConnection connection = new URL(url).openConnection();

            try (InputStream in = connection.getInputStream()) {
                text =
                    new BufferedReader(new InputStreamReader(in, UTF_8))
                    .lines()
                    .collect(joining(LF, EMPTY, LF));
            }
        } catch (Exception exception) {
            log.warn("Cannot read " + url, exception);
        }

        try {
            String[] ids = matchingStandardLicenseIds(text);

            value = LicenseInfoFactory.parseSPDXLicenseString(ids[0]);
        } catch (Exception exception) {
            log.warn("Cannot find license for " + url);

            value =
                new ExtractedLicenseInfo(null, text,
                                         null, new String[] { url }, null);
        }

        return value;
    }
}
