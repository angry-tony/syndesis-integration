/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.syndesis.core.Json;
import io.syndesis.core.MavenProperties;
import io.syndesis.core.SuppressFBWarnings;
import io.syndesis.dao.extension.ExtensionDataManager;
import io.syndesis.dao.init.ReadApiClientData;
import io.syndesis.dao.manager.DaoConfiguration;
import io.syndesis.model.Kind;
import io.syndesis.model.ModelData;
import io.syndesis.model.action.Action;
import io.syndesis.model.action.ConnectorAction;
import io.syndesis.model.action.ConnectorDescriptor;
import io.syndesis.model.connection.Connection;
import io.syndesis.model.connection.Connector;
import io.syndesis.model.connection.ConnectorTemplate;
import io.syndesis.model.integration.Integration;
import io.syndesis.model.integration.SimpleStep;
import io.syndesis.model.integration.Step;
import io.syndesis.project.converter.DefaultProjectGenerator;
import io.syndesis.project.converter.ProjectGenerator;
import io.syndesis.project.converter.ProjectGeneratorConfiguration;
import io.syndesis.project.converter.ProjectGeneratorProperties;
import io.syndesis.project.converter.visitor.StepVisitorFactoryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

@SpringBootApplication(
    exclude = {
        DaoConfiguration.class,
        ProjectGeneratorConfiguration.class
    }
)
public class Application implements ApplicationRunner {
    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${to:image}")
    private String to;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    @SuppressFBWarnings("DM_EXIT")
    public void run(ApplicationArguments args) {
        try {
            System.out.println("To: "+to); //NOPMD
            generateIntegrationProject(new File(to));
        } catch (IOException e) {
            e.printStackTrace(); //NOPMD
            System.exit(1); //NOPMD
        }
    }


    private void generateIntegrationProject(File project) throws IOException {
        final ReadApiClientData reader = new ReadApiClientData();
        final ArrayList<Step> steps = new ArrayList<>();

        String deploymentText;
        try (InputStream is = resourceLoader.getResource("io/syndesis/dao/deployment.json").getInputStream()) {
            deploymentText = reader.from(is);
        }

        final List<ModelData<?>> modelList = reader.readDataFromString(deploymentText);
        for (final ModelData<?> model : modelList) {
            if (model.getKind() == Kind.Connector) {
                final Connector connector = (Connector) model.getData();
                for (final Action<?> action : connector.getActions()) {
                    steps.add(
                        new SimpleStep.Builder()
                            .stepKind("endpoint")
                            .connection(new Connection.Builder()
                                .connector(connector)
                                .connectorId(connector.getId())
                                .build())
                            .action(action)
                            .build()
                    );
                }
            }

            if (model.getKind() == Kind.ConnectorTemplate) {
                final ConnectorTemplate template = (ConnectorTemplate) model.getData();
                steps.add(
                    new SimpleStep.Builder()
                        .stepKind("endpoint")
                        .connection(new Connection.Builder()
                            .connectorId("connector-" + template.getId())
                            .build())
                        .action(new ConnectorAction.Builder()
                            .descriptor(new ConnectorDescriptor.Builder()
                                .camelConnectorGAV(template.getCamelConnectorGAV())
                                .camelConnectorPrefix(template.getCamelConnectorPrefix())
                                .build())
                            .build())
                        .build()
                );
            }
        }

        try {
            final ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
            final Resource[] resources = resolver.getResources("classpath:/META-INF/syndesis/connector/*.json");

            if (resources != null) {
                for (Resource resource: resources) {
                    Connector connector = Json.mapper().readValue(resource.getInputStream(), Connector.class);

                    if (connector != null) {
                        for (final Action<?> action : connector.getActions()) {
                            steps.add(
                                new SimpleStep.Builder()
                                    .stepKind("endpoint")
                                    .connection(new Connection.Builder()
                                        .connector(connector)
                                        .connectorId(connector.getId())
                                        .build())
                                    .action(action)
                                    .build()
                            );
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // ignore
        }

        Integration integration = new Integration.Builder()
            .id("integration")
            .name("Integration")
            .description("This integration is used to prime the .m2 repo")
            .steps( steps )
            .build();

        generate(integration, project);
    }

    @SuppressWarnings("PMD.UseProperClassLoader")
    private static void generate(Integration integration, File targetDir) throws IOException {
        MavenProperties mavenProperties = new MavenProperties();
        ProjectGeneratorProperties generatorProperties = new ProjectGeneratorProperties(mavenProperties);
        StepVisitorFactoryRegistry registry = new StepVisitorFactoryRegistry(Collections.emptyList());
        ProjectGenerator generator = new DefaultProjectGenerator(generatorProperties, registry, null, new ExtensionDataManager(null, null));

        Path dir =targetDir.toPath();
        Files.createDirectories( dir);
        Files.write(dir.resolve("pom.xml"), generator.generatePom(integration));

        dir = dir.resolve("src/main/java/io/syndesis/example");
        Files.createDirectories( dir);

        String resourceFile = "io/syndesis/project/converter/templates/Application.java.mustache";
        try (InputStream is = Application.class.getClassLoader().getResourceAsStream(resourceFile) )  {
            Files.write(dir.resolve("Application.java"), readAllBytes(is));
        }

    }

    public static byte[] readAllBytes(InputStream is) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024 * 4];
            for (int len; (len = is.read(buffer)) != -1; ) { //NOPMD
                os.write(buffer, 0, len);
            }
            os.flush();
            return os.toByteArray();
        }
    }


    // Helper method to help construct maps with concise syntax
    private static Map<String, String> map(Object... values) {
        HashMap<String, String> rc = new HashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            rc.put(values[i].toString(), values[i + 1].toString());
        }
        return rc;
    }
}
