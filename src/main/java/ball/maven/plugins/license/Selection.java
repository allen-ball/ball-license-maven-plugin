package ball.maven.plugins.license;
/*-
 * ##########################################################################
 * License Maven Plugin
 * $Id$
 * $HeadURL$
 * %%
 * Copyright (C) 2020 Allen D. Ball
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
import java.util.Map;
import lombok.Data;
import org.spdx.rdfparser.license.AnyLicenseInfo;

/**
 * {@code <selection/>} parameter.
 *
 * {@bean.info}
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 * @version $Revision$
 */
@Data
public class Selection implements Map.Entry<String,AnyLicenseInfo> {
    private String artifact = null;
    private AnyLicenseInfo license = null;

    @Override
    public String getKey() { return artifact; }

    @Override
    public AnyLicenseInfo getValue() { return license; }

    @Override
    public AnyLicenseInfo setValue(AnyLicenseInfo value) {
        throw new UnsupportedOperationException();
    }
}
