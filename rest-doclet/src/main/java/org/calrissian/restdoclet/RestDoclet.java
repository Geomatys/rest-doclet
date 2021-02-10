/** *****************************************************************************
 * Copyright (C) 2014 The Calrissian Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ****************************************************************************** */
package org.calrissian.restdoclet;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.StandardDoclet;
import jdk.javadoc.doclet.DocletEnvironment;
import org.calrissian.restdoclet.collector.Collector;
import org.calrissian.restdoclet.collector.jaxrs.JaxRSCollector;
import org.calrissian.restdoclet.collector.spring.SpringCollector;
import org.calrissian.restdoclet.model.ClassDescriptor;
import org.calrissian.restdoclet.writer.Writer;
import org.calrissian.restdoclet.writer.simple.SimpleHtmlWriter;
import org.calrissian.restdoclet.writer.swagger.SwaggerWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import jdk.javadoc.doclet.Reporter;
import static org.calrissian.restdoclet.Configuration.ConfigOption.API_VERSION;
import static org.calrissian.restdoclet.Configuration.ConfigOption.BASEPATH;
import static org.calrissian.restdoclet.Configuration.ConfigOption.DISPLAY_ONLY;
import static org.calrissian.restdoclet.Configuration.ConfigOption.OUTPUT_FORMAT;
import static org.calrissian.restdoclet.Configuration.ConfigOption.STYLESHEET;
import static org.calrissian.restdoclet.Configuration.ConfigOption.TITLE;

import static org.calrissian.restdoclet.Configuration.getOptionLength;

public class RestDoclet implements Doclet {

    @Override
    public void init(Locale locale, Reporter reporter) {
        // do nothing
    }

    @Override
    public String getName() {
        return "RestDoclet";
    }

    private Map<String, String> options = new HashMap<>();

    @Override
    public Set<? extends Option> getSupportedOptions() {
        Set<ConfigOption> options = new HashSet<>();
        options.add(new ConfigOption(OUTPUT_FORMAT));
        //Legacy Options
        options.add(new ConfigOption(TITLE));
        options.add(new ConfigOption(STYLESHEET));
        //Swagger options
        options.add(new ConfigOption(API_VERSION));
        options.add(new ConfigOption(DISPLAY_ONLY));
        options.add(new ConfigOption(BASEPATH) );
        return options;
    };


    /**
     * Generate documentation here. This method is required for all doclets.
     *
     * @return true on success.
     */
    @Override
    public boolean run(DocletEnvironment root) {

        Configuration config = new Configuration(options);

        Collection<ClassDescriptor> classDescriptors = new ArrayList<>();

        final Collection<Collector> collectors = Arrays.<Collector>asList(
            new SpringCollector(root.getDocTrees()),
            new JaxRSCollector(root.getDocTrees())
        );

        for (Collector collector : collectors) {
            classDescriptors.addAll(collector.getDescriptors(root));
        }

        Writer writer;
        if (config.getOutputFormat().equals(SwaggerWriter.OUTPUT_OPTION_NAME)) {
            writer = new SwaggerWriter();
        } else {
            writer = new SimpleHtmlWriter();
        }

        try {
            writer.write(classDescriptors, config);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Required to validate command line options.
     *
     * @param option option name
     * @return option length
     */
    public static int optionLength(String option) {
        return getOptionLength(option);
    }

    /**
     * @return language version (hard coded to SourceVersion.RELEASE_11)
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_11;
    }

    public class ConfigOption implements Doclet.Option {

        private final String name;
        private final boolean hasArg;
        private final String description;
        private final String parameters;

        public ConfigOption(Configuration.ConfigOption co) {
            this.name = co.getOption();
            this.hasArg = true;
            this.description = co.getDescription();
            this.parameters = co.getDefaultValue();
        }

        @Override
        public int getArgumentCount() {
            return 1;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Option.Kind getKind() {
            return Option.Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return List.of(name);
        }

        @Override
        public String getParameters() {
            return hasArg ? parameters : "";
        }

        @Override
        public boolean process(String option, List<String> arguments) {
            options.put(option, arguments.get(0));
            return true;
        }
    }
}
