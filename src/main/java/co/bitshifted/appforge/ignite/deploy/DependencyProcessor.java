/*
 *
 *  * Copyright (c) 2022-2022  Bitshift D.O.O (http://bitshifted.co)
 *  *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 */

package co.bitshifted.appforge.ignite.deploy;

import co.bitshifted.appforge.common.model.CpuArch;
import co.bitshifted.appforge.common.model.OperatingSystem;
import co.bitshifted.appforge.ignite.model.JavaDependency;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class DependencyProcessor {

    private static final String[] JAVAFX_PLATFORM_VALUES = new String[]{"linux", "linux-aarch64", "mac", "mac-aarch64", "win"};
    private static final String JAVAFX_GROUP = "org.openjfx";

    private final MavenProject project;
    private final MavenSession session;
    private final BuildPluginManager manager;
    private final Log loger;
    private final DigestUtils digestUtils;

    public DependencyProcessor(MavenProject project, MavenSession session, BuildPluginManager manager, Log logger) {
        this.project = project;
        this.loger = logger;
        this.session = session;
        this.manager = manager;
        this.digestUtils = new DigestUtils(MessageDigestAlgorithms.SHA_256);
    }

    public DependencyResolutionResult resolveDependencies(Set<OperatingSystem> supportedOs) throws IOException {
        DependencyResolutionResult result = new DependencyResolutionResult();
        Set<JavaDependency> allDeps = calculateDependenciesInt();
        Set<JavaDependency> javafxPlatformDeps = allDeps.stream()
            .filter(i -> JAVAFX_GROUP.equals(i.getGroupId()) && i.getClassifier() != null)
            .collect(Collectors.toSet());
        Set<JavaDependency> commonDeps = allDeps.stream().filter(i -> !JAVAFX_GROUP.equals(i.getGroupId()))
            .collect(Collectors.toSet());

        if(!javafxPlatformDeps.isEmpty()) {
            downloadJavafxDependencies(javafxPlatformDeps);
            for(OperatingSystem os : supportedOs) {
                Set<JavaDependency> osDeps = new HashSet<>();
                for(CpuArch arch : CpuArch.values()) {
                   osDeps.addAll(resolveJavafxDependencies(javafxPlatformDeps, os, arch));
                }
                result.setDependencies(os, osDeps);
            }
        }
        result.setCommon(commonDeps);

       return result;
    }

    private Set<JavaDependency> calculateDependenciesInt() throws IOException {
        Set<JavaDependency> deps = project.getArtifacts().stream().filter(d -> !d.getScope().equals("test")).map(JavaDependency::new).collect(Collectors.toSet());
        for(JavaDependency d : deps) {
            if (d.getDependencyFile() != null) {
                d.setSha256(digestUtils.digestAsHex(d.getDependencyFile()));
            }
        }
        // add build artifact to dependencies
        Artifact artifact = project.getArtifact();
        if (artifact.getFile() != null) {
            JavaDependency mainArtifact = new JavaDependency(artifact);
            mainArtifact.setSha256(digestUtils.digestAsHex(artifact.getFile()));
            deps.add(mainArtifact);
        }
        return deps;
    }

    private void downloadJavafxDependencies(Set<JavaDependency> input) {
        Plugin depPlugin = project.getPluginManagement().getPlugins().stream().filter(p -> "org.apache.maven.plugins".equals(p.getGroupId()) && "maven-dependency-plugin".equals(p.getArtifactId()))
            .findFirst()
            .orElse(MojoExecutor.plugin("org.apache.maven.plugins", "maven-dependency-plugin", "3.3.0"));
        if(!input.isEmpty()) {
            for(String platform : JAVAFX_PLATFORM_VALUES) {
                loger.info("Collecting JavaFX dependencies for platform: " + platform);
                input.stream().forEach(d -> {
                    String artifact = String.format("%s:%s:%s:%s:%s",d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getType(), platform);
                    loger.info("Getting artifact " + artifact);
                    try {
                        MojoExecutor.executeMojo(
                            depPlugin,
                            MojoExecutor.goal("get"),
                            MojoExecutor.configuration(MojoExecutor.element("artifact", artifact)),
                            MojoExecutor.executionEnvironment(session, manager)
                        );
                    } catch (MojoExecutionException ex) {
                        loger.error("Failed to get artifact " + artifact, ex);
                    }
                });

            }
        }

    }

    private Set<JavaDependency> resolveJavafxDependencies(Set<JavaDependency> input, OperatingSystem os, CpuArch arch) throws IOException {
        if(os == OperatingSystem.WINDOWS && arch == CpuArch.AARCH64) {
            return Collections.emptySet();
        }
        Set<JavaDependency> out = new HashSet<>();
        String classifier = javafxOperatingSystemClassifier(os, arch);
        for(JavaDependency d : input) {
            if(classifier.equals(d.getClassifier())) {
                d.setPlatformSpecific(true);
                d.setSupportedOs(os);
                d.setSupportedCpuArch(arch);
                out.add(d);
            } else {
                Artifact artifact = new DefaultArtifact(d.getGroupId(), d.getArtifactId(), d.getVersion(), null, d.getType(), classifier, d.getArtifact().getArtifactHandler());
                String path = session.getLocalRepository().pathOf(artifact);
                Path repoDirPath = Paths.get(session.getLocalRepository().getBasedir());
                Path artifactPath = repoDirPath.resolve(path);
                JavaDependency dep = new JavaDependency(artifact, artifactPath.toFile(), os, arch);
                dep.setSha256(digestUtils.digestAsHex(artifactPath.toFile()));
                out.add(dep);
            }
        }
        return out;
    }

    private String javafxOperatingSystemClassifier(OperatingSystem os, CpuArch arch) {
        switch (os) {
            case LINUX:
                switch (arch) {
                    case X64:
                        return OperatingSystem.LINUX.getDisplay();
                    case AARCH64:
                        return OperatingSystem.LINUX.getDisplay() + "-" + CpuArch.AARCH64.getDisplay();
                }
            case MAC:
                switch (arch) {
                    case X64:
                        return OperatingSystem.MAC.getDisplay();
                    case AARCH64:
                        return OperatingSystem.MAC.getDisplay() + "-" + CpuArch.AARCH64.getDisplay();
                }
            case WINDOWS:
                return "win";
            default:
                throw new IllegalArgumentException("Unsupported operating system: " + os);
        }
    }

    private Set<JavaDependency> javafxDependencies(Set<JavaDependency> input) {
        return input.stream().filter(i -> JAVAFX_GROUP.equals(i.getGroupId()) && i.getClassifier() != null).collect(Collectors.toSet());
    }

   private Set<JavaDependency> cleanup(DependencyResolutionResult result, Set<JavaDependency> allDeps) {
       return allDeps.stream().filter(d -> !result.getLinux().contains(d) && !result.getMac().contains(d) && !result.getWindows().contains(d)).collect(Collectors.toSet());
   }
}
