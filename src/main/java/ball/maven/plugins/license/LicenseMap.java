package ball.maven.plugins.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.spdx.rdfparser.license.License;
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

    /**
     * Sole constructor.
     */
    public LicenseMap() {
        super(String.CASE_INSENSITIVE_ORDER);

        try {
            for (String key : LICENSES.getSpdxListedLicenseIds()) {
                put(key, LICENSES.getListedLicenseById(key));
            }

            URL url =
                getClass().getClassLoader()
                .getResource("resources/licenses-full.json");
            for (JsonNode node :
                     new ObjectMapper().readTree(url).at("/licenses")) {
                String id = node.at("/id").asText();
                String spdx = node.at("/identifiers/spdx[0]").asText();

                if (containsKey(spdx)) {
                    put(id, get(spdx));
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
            if (key instanceof CharSequence) {
                String string = key.toString();

                value =
                    Stream.of(string,
                              string.replaceAll("[\\p{Space}]", "-"),
                              string.replaceAll("licence", "license"))
                    .map(t -> super.get(t))
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            }
        }

        return value;
    }
}
