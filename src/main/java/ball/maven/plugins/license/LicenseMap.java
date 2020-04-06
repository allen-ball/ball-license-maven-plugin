package ball.maven.plugins.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spdx.rdfparser.license.License;
/* import org.spdx.rdfparser.license.ListedExceptions; */
import org.spdx.rdfparser.license.ListedLicenses;

/**
 * {@link java.util.Map} implementation that relates SPDX ID and known
 * aliases to {@link License} sourced from
 * {@link ListedLicenses#getListedLicenses()}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Named @Singleton
@Slf4j
public class LicenseMap extends TreeMap<String,License> {
    private static final ListedLicenses LICENSES =
        ListedLicenses.getListedLicenses();
    /*
     * private static final ListedExceptions EXCEPTIONS =
     *     ListedExceptions.getListedExceptions();
     */

    /**
     * {@code resources/licenses-full.json} read from SPDX artifacts.
     */
    public static final JsonNode LICENSES_FULL_JSON;

    static {
        try {
            LICENSES_FULL_JSON =
                new ObjectMapper()
                .readTree(LicenseMap.class
                          .getClassLoader()
                          .getResource("resources/licenses-full.json"));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    /**
     * Sole constructor.
     */
    @Inject
    public LicenseMap() {
        super(String.CASE_INSENSITIVE_ORDER);

        try {
            for (String key : LICENSES.getSpdxListedLicenseIds()) {
                License license = LICENSES.getListedLicenseById(key);

                put(key, license);
                putIfAbsent(license.getName(), license);
            }

            for (JsonNode node : LICENSES_FULL_JSON.at("/licenses")) {
                String spdx = node.at("/identifiers/spdx/0").asText();

                if (containsKey(spdx)) {
                    Stream.of(node.at("/id").asText(),
                              node.at("/name").asText())
                        .filter(StringUtils::isNotBlank)
                        .forEach(t -> computeIfAbsent(t, k -> get(spdx)));
                }
            }
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @PostConstruct
    public void init() { }

    @PreDestroy
    public void destroy() {
        log.debug(getClass().getSimpleName() + ".size() = " + size());
    }

    @Override
    public License get(Object key) {
        License value = super.get(key);

        if (value == null) {
            if (key instanceof String) {
                String string = ((String) key).trim();

                value =
                    Stream.of(string.replaceAll("(?i)[\\p{Space}]+", "-"),
                              string.replaceAll("(?i)(V(ERSION)?)[\\p{Space}]?([\\p{Digit}])", "$3"))
                    .filter(t -> (! string.equals(t)))
                    .map(t -> get(t))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            }
        }

        return value;
    }
}
