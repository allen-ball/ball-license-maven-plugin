package ball.maven.plugins.license;
/*-
 * ##########################################################################
 * License Maven Plugin
 * %%
 * Copyright (C) 2020 - 2022 Allen D. Ball
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ##########################################################################
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.License;
import org.spdx.rdfparser.license.LicenseInfoFactory;
/* import org.spdx.rdfparser.license.ListedExceptions; */
import org.spdx.rdfparser.license.ListedLicenses;

import static ball.maven.plugins.license.LicenseUtilityMethods.isFullySpdxListed;
import static org.apache.commons.lang3.StringUtils.SPACE;

/**
 * {@link java.util.Map} implementation that relates SPDX ID and known
 * aliases to {@link License} sourced from
 * {@link ListedLicenses#getListedLicenses()}.
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@Named @Singleton
@Slf4j
public class LicenseMap extends TreeMap<String,AnyLicenseInfo> implements DefaultMethods {
    private static final long serialVersionUID = 8959458091372664080L;

    private static final String ONLY_USE_LOCAL_LICENSES = "SPDXParser.OnlyUseLocalLicenses";

    /**
     * {@code resources/licenses-full.json} read from SPDX artifacts.
     */
    public static final JsonNode LICENSES_FULL_JSON;

    static {
        if (System.getProperty(ONLY_USE_LOCAL_LICENSES) == null) {
            System.setProperty(ONLY_USE_LOCAL_LICENSES, String.valueOf(true));
        }

        try {
            LICENSES_FULL_JSON =
                new ObjectMapper()
                .readTree(LicenseMap.class.getClassLoader().getResource("resources/licenses-full.json"));
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    /**
     * Sole constructor.
     */
    @Inject
    public LicenseMap() { super(String.CASE_INSENSITIVE_ORDER); }

    @PostConstruct
    public void init() throws Exception {
        ListedLicenses licenses = ListedLicenses.getListedLicenses();
        /* ListedExceptions exceptions = ListedExceptions.getListedExceptions(); */

        for (String key : licenses.getSpdxListedLicenseIds()) {
            License license = licenses.getListedLicenseById(key);

            put(key, license);
            putIfAbsent(license.getName(), license);
        }

        for (JsonNode node : LICENSES_FULL_JSON.at("/licenses")) {
            String spdx = node.at("/identifiers/spdx/0").asText();

            if (containsKey(spdx)) {
                Stream.of(node.at("/id").asText(), node.at("/name").asText())
                    .map(t -> t.trim())
                    .map(t -> t.replaceAll("[\\p{Space}]+", SPACE))
                    .filter(StringUtils::isNotBlank)
                    .forEach(t -> computeIfAbsent(t, k -> get(spdx)));
            }
        }

        Properties aliases = getXMLProperties("aliases");

        for (String id : aliases.stringPropertyNames()) {
            AnyLicenseInfo value = LicenseInfoFactory.parseSPDXLicenseString(id);

            if (! isFullySpdxListed(value)) {
                throw new IllegalArgumentException(id);
            }

            Stream.of(aliases.getProperty(id).split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .forEach(t -> putIfAbsent(t, value));
        }
    }

    @PreDestroy
    public void destroy() {
        log.debug("{}.size() = {}", getClass().getSimpleName(), size());
    }

    @Override
    public AnyLicenseInfo get(Object key) {
        AnyLicenseInfo value = super.get(key);

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
