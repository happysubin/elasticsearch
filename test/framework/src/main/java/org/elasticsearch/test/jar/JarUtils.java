/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.test.jar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toUnmodifiableMap;

public final class JarUtils {

    private JarUtils() {}

    /**
     * Creates a jar file with the given manifest and list of (empty) jar file entry names. The
     * jar file entries will be added to the jar, but will all be empty (no contents).
     *
     * @param dir the directory in which the jar will be created
     * @param name the name of the jar file
     * @param manifest the manifest, may be null
     * @param files the list of jar entry names, to be added to the jar
     * @return the path of the jar file
     * @throws IOException if an I/O error occurs
     */
    public static Path createJar(Path dir, String name, Manifest manifest, String... files) throws IOException {
        Path jarpath = dir.resolve(name);
        UncheckedIOFunction<OutputStream, JarOutputStream> jarOutFunc;
        if (manifest == null) {
            jarOutFunc = os -> new JarOutputStream(os);
        } else {
            jarOutFunc = os -> new JarOutputStream(os, manifest);
        }
        try (var os = Files.newOutputStream(jarpath, StandardOpenOption.CREATE); var out = jarOutFunc.apply(os)) {
            for (String file : files) {
                out.putNextEntry(new JarEntry(file));
            }
        }
        return jarpath;
    }

    /**
     * Creates a jar file with the given entries.
     *
     * @param jarfile the jar file path
     * @param entries map of entries to add; jar entry name to byte contents
     * @throws IOException if an I/O error occurs
     */
    public static void createJarWithEntries(Path jarfile, Map<String, byte[]> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(jarfile); JarOutputStream jos = new JarOutputStream(out)) {
            for (var entry : entries.entrySet()) {
                String name = entry.getKey();
                jos.putNextEntry(new JarEntry(name));
                var bais = new ByteArrayInputStream(entry.getValue());
                bais.transferTo(jos);
                jos.closeEntry();
            }
        }
    }

    /**
     * Creates a jar file with the given entries. Entry values are converted to bytes using UTF-8.
     *
     * @param jarfile the jar file path
     * @param entries map of entries to add; jar entry name to String contents
     * @throws IOException if an I/O error occurs
     */
    public static void createJarWithEntriesUTF(Path jarfile, Map<String, String> entries) throws IOException {
        var map = entries.entrySet().stream().collect(toUnmodifiableMap(Map.Entry::getKey, v -> v.getValue().getBytes(UTF_8)));
        createJarWithEntries(jarfile, map);
    }

    /**
     * Creates a class loader for the given jar file.
     * @param path Path to the jar file to load
     * @return A URLClassLoader that will load classes from the jar. It should be closed when no longer needed.
     */
    public static URLClassLoader loadJar(Path path) {
        try {
            URL[] urls = new URL[] { path.toUri().toURL() };
            return URLClassLoader.newInstance(urls, JarUtils.class.getClassLoader());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static ModuleLayer.Controller loadModule(Path path, ClassLoader loader, String name) {
        var finder = ModuleFinder.of(path.getParent());
        var cf = Configuration.resolveAndBind(finder, List.of(ModuleLayer.boot().configuration()), ModuleFinder.of(), Set.of(name));
        return AccessController.doPrivileged(
            (PrivilegedAction<ModuleLayer.Controller>) () -> ModuleLayer.defineModulesWithOneLoader(cf, List.of(ModuleLayer.boot()), loader)
        );
    }

    @FunctionalInterface
    interface UncheckedIOFunction<T, R> {
        R apply(T t) throws IOException;
    }
}
