package ball.maven.plugins.license;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Stream;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;

import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * {@link ExtractedLicenseInfo} implementation.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
public class TextLicenseInfo extends ExtractedLicenseInfo {

    /**
     * Sole constructor.
     *
     * @param   id              The observed license ID.
     * @param   text            The observed license text.
     * @param   urls            The URLs to add.
     */
    public TextLicenseInfo(String id, String text, String... urls) {
        super(isNotBlank(id) ? id : EMPTY,
              isNotBlank(text) ? text : EMPTY);

        setName(getLicenseId());
        setSeeAlso((urls != null) ? urls : new String[] { });
    }

    /**
     * See {@link #setSeeAlso(String[])}.
     *
     * @param   urls            The URLs to add.
     */
    public void addSeeAlso(String... urls) { addSeeAlso(this, urls); }

    /**
     * See {@link #setSeeAlso(String[])}.
     *
     * @param   license         The {@link ExtractedLicenseInfo} instance.
     * @param   urls            The URLs to add.
     */
    public static void addSeeAlso(ExtractedLicenseInfo license,
                                  String... urls) {
        String[] seeAlso = license.getSeeAlso();
        Set<String> set =
            Stream.of((seeAlso != null) ? seeAlso : new String[] { })
            .collect(toSet());

        if (urls != null) {
            Collections.addAll(set, urls);

            license.setSeeAlso(set.toArray(new String[] { }));
        }
    }
}
