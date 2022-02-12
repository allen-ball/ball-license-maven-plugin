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
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * URL {@link org.spdx.rdfparser.license.ExtractedLicenseInfo}
 * implementation.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
public class URLLicenseInfo extends TextLicenseInfo {

    /**
     * Sole constructor.
     *
     * @param   id              The observed license ID.
     * @param   urls            The URLs to add.
     */
    public URLLicenseInfo(String id, String... urls) {
        super(isNotBlank(id) ? id : urls[0], isNotBlank(id) ? id : urls[0], urls);
    }
}
