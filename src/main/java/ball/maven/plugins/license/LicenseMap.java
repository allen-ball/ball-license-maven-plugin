package ball.maven.plugins.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.util.TreeMap;
import javax.inject.Named;
import javax.inject.Singleton;
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
}
