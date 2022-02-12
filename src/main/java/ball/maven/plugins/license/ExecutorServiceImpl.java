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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Singleton {@link java.util.concurrent.ExecutorService}
 * ({@link ThreadPoolExecutor}).
 *
 * @author {@link.uri mailto:ball@hcf.dev Allen D. Ball}
 */
@Named @Singleton
@Slf4j
public class ExecutorServiceImpl extends ThreadPoolExecutor {
    private static final int SIZE = 8;

    /**
     * Sole constructor.
     */
    public ExecutorServiceImpl() {
        super(SIZE, SIZE, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }
}
