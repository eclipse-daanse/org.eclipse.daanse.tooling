/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   SmartCity Jena - initial
 *   Stefan Bischof (bipolis.org) - initial
 */
package org.eclipse.daanse.tooling.emf.codegen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.emf.codegen.ecore.generator.Generator;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenBaseGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.impl.GenModelFactoryImpl;
import org.eclipse.emf.codegen.ecore.genmodel.impl.GenModelPackageImpl;
import org.eclipse.emf.codegen.util.CodeGenUtil;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.fennec.emf.osgi.codegen.adapter.BNDGeneratorAdapterFactory;


/**
 * Unified Maven Mojo for generating EMF model code from either a GenModel or
 * Ecore file.
 *
 * <p>
 * The mojo automatically detects which mode to use based on which file
 * parameter is set:
 * </p>
 * <ul>
 * <li>If {@code genmodelFile} is set: Uses the existing GenModel file</li>
 * <li>If {@code ecoreFile} is set: Creates a GenModel programmatically from the
 * Ecore</li>
 * <li>If both are set: GenModel takes precedence</li>
 * </ul>
 *
 * <p>
 * Example usage with GenModel:
 * </p>
 *
 * <pre>{@code
 * <configuration>
 *   <genmodelFile>model/mymodel.genmodel</genmodelFile>
 *   <outputDirectory>target/generated-sources/emf</outputDirectory>
 * </configuration>
 * }</pre>
 *
 * <p>
 * Example usage with Ecore (GenModel settings from annotations or Maven
 * config):
 * </p>
 *
 * <pre>{@code
 * <configuration>
 *   <ecoreFile>model/mymodel.ecore</ecoreFile>
 *   <basePackage>com.example.model</basePackage>
 *   <outputDirectory>target/generated-sources/emf</outputDirectory>
 * </configuration>
 * }</pre>
 *
 * <p>
 * Example usage with Ecore using GenModel annotations in the ecore file:
 * </p>
 *
 * <pre>{@code
 * <configuration>
 *   <ecoreFile>model/mymodel.ecore</ecoreFile>
 *   <outputDirectory>target/generated-sources/emf</outputDirectory>
 *   <!-- basePackage, prefix etc. read from ecore GenModel annotations -->
 * </configuration>
 * }</pre>
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class EmfGenerateMojo extends AbstractMojo {

    public static final String INCLUDE_GEN_MODEL_FOLDER = "includeGenModelFolder";
    public static final String INCLUDE_ECORE_FOLDER = "includeEcoreFolder";
    public static final String ECORE_BUNDLE_LOCATION = "ecoreBundleLocation";
    public static final String GENMODEL_ANNOTATION_SOURCE = "http://www.eclipse.org/emf/2002/GenModel";

    /**
     * Registry mapping EPackage nsURI to GenPackage for dependency resolution.
     */
    private final Map<String, GenPackage> genPackageRegistry = new HashMap<>();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * All projects in the reactor (for resolving reactor dependencies before they
     * are packaged).
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    private java.util.List<MavenProject> reactorProjects;

    // ========== Input File Parameters ==========

    /**
     * Path to the genmodel file. If set, generation uses the existing GenModel.
     * Takes precedence over ecoreFile if both are set.
     */
    @Parameter(property = "emf.genmodelFile")
    private File genmodelFile;

    /**
     * Path to the ecore file. If set (and genmodelFile is not set), a GenModel is
     * created programmatically.
     */
    @Parameter(property = "emf.ecoreFile")
    private File ecoreFile;

    // ========== Output Parameters ==========

    /**
     * Output directory for generated code, relative to the project base directory.
     */
    @Parameter(property = "emf.outputDirectory", defaultValue = "target/generated-sources/emf")
    private String outputDirectory;

    /**
     * Optional location where the genmodel will be included in the built artifact.
     * Used for OSGi capability generation.
     */
    @Parameter(property = "emf.genmodelIncludeLocation")
    private String genmodelIncludeLocation;

    /**
     * The bundle-relative path to the ecore file for the @EPackage annotation.
     * Example: "model/catalog.ecore"
     */
    @Parameter(property = "emf.ecoreBundleLocation")
    private String ecoreBundleLocation;

    // ========== GenModel Configuration (for Ecore mode) ==========

    /**
     * Base package for generated code. If not specified, derived from EPackage
     * nsURI or GenModel annotation in ecore file.
     */
    @Parameter(property = "emf.basePackage")
    private String basePackage;

    /**
     * Prefix for generated class names. If not specified, derived from EPackage
     * name or GenModel annotation in ecore file.
     */
    @Parameter(property = "emf.prefix")
    private String prefix;

    /**
     * File extension for model resources.
     */
    @Parameter(property = "emf.fileExtension")
    private String fileExtension;

    /**
     * Resource type for model resources (XMI, XML, BASIC, NONE). Determines whether
     * a ResourceFactory is generated.
     */
    @Parameter(property = "emf.resource")
    private String resource;

    /**
     * Whether to generate OSGi-compatible code.
     */
    @Parameter(property = "emf.osgiCompatible", defaultValue = "true")
    private boolean osgiCompatible;

    /**
     * Whether to suppress generation of interfaces (only impl classes).
     */
    @Parameter(property = "emf.suppressInterfaces", defaultValue = "false")
    private boolean suppressInterfaces;

    /**
     * Whether to suppress EMF model types and use Java native types.
     */
    @Parameter(property = "emf.suppressEMFTypes", defaultValue = "false")
    private boolean suppressEMFTypes;

    /**
     * Whether to suppress EMF metadata.
     */
    @Parameter(property = "emf.suppressEMFMetaData", defaultValue = "false")
    private boolean suppressEMFMetaData;

    /**
     * Whether to suppress GenModel annotations in generated code.
     */
    @Parameter(property = "emf.suppressGenModelAnnotations", defaultValue = "false")
    private boolean suppressGenModelAnnotations;

    /**
     * Whether to make constructors public.
     */
    @Parameter(property = "emf.publicConstructors", defaultValue = "false")
    private boolean publicConstructors;

    /**
     * Root class that generated model objects extend.
     */
    @Parameter(property = "emf.rootExtendsClass")
    private String rootExtendsClass;

    /**
     * Root interface that generated model objects implement.
     */
    @Parameter(property = "emf.rootExtendsInterface")
    private String rootExtendsInterface;

    /**
     * Copyright text to include in generated files.
     */
    @Parameter(property = "emf.copyrightText")
    private String copyrightText;

    /**
     * Target file path in JAR for the ecore file (e.g., "model/catalog.ecore"). If
     * not set (null or empty), defaults to "model/{epackage-name}.ecore".
     */
    @Parameter(property = "emf.ecoreTargetFile")
    private String ecoreTargetFile;

    /**
     * Target file path in JAR for the genmodel file (e.g.,
     * "model/catalog.genmodel"). If not set (null or empty), defaults to
     * "model/{epackage-name}.genmodel".
     */
    @Parameter(property = "emf.genmodelTargetFile")
    private String genmodelTargetFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Determine which mode to use
        boolean useGenmodel = genmodelFile != null && genmodelFile.exists();
        boolean useEcore = ecoreFile != null && ecoreFile.exists();

        if (!useGenmodel && !useEcore) {
            // Check if files were specified but don't exist
            if (genmodelFile != null && !genmodelFile.exists()) {
                throw new MojoFailureException("GenModel file not found: " + genmodelFile.getAbsolutePath());
            }
            if (ecoreFile != null && !ecoreFile.exists()) {
                throw new MojoFailureException("Ecore file not found: " + ecoreFile.getAbsolutePath());
            }
            throw new MojoFailureException("Either 'genmodelFile' or 'ecoreFile' must be specified. "
                    + "Use <genmodelFile> for existing GenModel files or <ecoreFile> for Ecore files.");
        }

        File baseDir = project.getBasedir();
        File outputDir = new File(baseDir, outputDirectory);
        outputDir.mkdirs();

        Optional<String> error;
        if (useGenmodel) {
            getLog().info("EMF Code Generator - Using GenModel mode");
            getLog().info("  GenModel: " + genmodelFile);
            error = generateFromGenmodel(baseDir);
        } else {
            getLog().info("EMF Code Generator - Using Ecore mode");
            getLog().info("  Ecore: " + ecoreFile);
            error = generateFromEcore(baseDir);
        }

        if (error.isPresent()) {
            throw new MojoFailureException(error.get());
        }

        // Post-process generated sources to remove source location attributes from
        // @EPackage annotations
        postProcessEPackageAnnotations(outputDir);

        project.addCompileSourceRoot(outputDir.getAbsolutePath());
        getLog().info("Added " + outputDir.getAbsolutePath() + " to compile source roots");

        // Copy model files (ecore, genmodel) to target/classes for JAR inclusion
        copyModelFilesToTarget();
    }

    // ==================== GenModel Mode ====================

    private Optional<String> generateFromGenmodel(File baseDir) {
        String projectName = project.getArtifactId();
        getLog().info("Running for genmodel " + genmodelFile + " in " + baseDir.getAbsolutePath());

        ResourceSet resourceSet = new ResourceSetImpl();
        try {
            configureEMF(resourceSet);
            setupURIMapping(resourceSet, baseDir, projectName);
            setupDependencyURIMappings(resourceSet);

            URI genModelUri = URI.createFileURI(genmodelFile.getAbsolutePath());
            getLog().info("Loading " + genModelUri.toString());

            Resource resource = resourceSet.getResource(genModelUri, true);
            if (!resource.getErrors().isEmpty()) {
                return Optional.of("Error loading GenModel: " + resource.getErrors().get(0).toString());
            }

            GenModel genModel = (GenModel) resource.getContents().get(0);
            getLog().info("Resolving all models");
            EcoreUtil.resolveAll(genModel);

            // Add usedGenPackages from dependencies based on referenced external packages
            addUsedGenPackagesFromDependencies(genModel);

            Diagnostic genModelDiagnostic = Diagnostician.INSTANCE.validate(genModel);
            if (genModelDiagnostic.getSeverity() != Diagnostic.OK) {
                getLog().error("GenModel is invalid");
                printDiagnostic(genModelDiagnostic, "");
                return Optional.of("GenModel validation failed");
            }

            // Override modelDirectory with configured outputDirectory
            String modelDirectory = "/" + projectName + (outputDirectory.startsWith("/") ? "" : "/") + outputDirectory;
            getLog().info("Setting modelDirectory: " + modelDirectory);
            genModel.setModelDirectory(modelDirectory);

            // Use OSGi templates if the genmodel has OSGi compatibility enabled
            boolean useOsgiTemplates = genModel.isOSGiCompatible();
            return runGenerator(genModel, genmodelFile.getAbsolutePath(), useOsgiTemplates);
        } finally {
            resourceSet.getResources().forEach(Resource::unload);
            resourceSet.getResources().clear();
        }
    }

    // ==================== Ecore Mode ====================

    private Optional<String> generateFromEcore(File baseDir) {
        String projectName = project.getArtifactId();
        getLog().info("Running for ecore " + ecoreFile + " in " + baseDir.getAbsolutePath());

        ResourceSet resourceSet = new ResourceSetImpl();
        try {
            configureEMF(resourceSet);
            setupURIMapping(resourceSet, baseDir, projectName);

            URI ecoreUri = URI.createFileURI(ecoreFile.getAbsolutePath());
            getLog().info("Loading Ecore from: " + ecoreUri);

            Resource ecoreResource = resourceSet.getResource(ecoreUri, true);
            if (!ecoreResource.getErrors().isEmpty()) {
                return Optional.of("Error loading Ecore: " + ecoreResource.getErrors().get(0).toString());
            }

            if (ecoreResource.getContents().isEmpty()) {
                return Optional.of("Ecore file is empty");
            }
            if (!(ecoreResource.getContents().get(0) instanceof EPackage)) {
                return Optional.of("Ecore file does not contain an EPackage");
            }

            EPackage ePackage = (EPackage) ecoreResource.getContents().get(0);
            getLog().info("Loaded EPackage: " + ePackage.getName() + " (" + ePackage.getNsURI() + ")");

            GenModel genModel = createGenModel(ePackage, projectName, resourceSet);

            getLog().info("Resolving all models");
            EcoreUtil.resolveAll(genModel);

            Diagnostic genModelDiagnostic = Diagnostician.INSTANCE.validate(genModel);
            if (genModelDiagnostic.getSeverity() == Diagnostic.ERROR) {
                getLog().error("GenModel validation failed");
                printDiagnostic(genModelDiagnostic, "");
                return Optional.of("GenModel validation failed");
            }

            logGenModelInfo(genModel);

            // Save the GenModel to resources for inclusion in JAR
            saveGenModelToResources(genModel, ePackage);

            // Collect referenced package names (to delete their generated files after
            // generation)
            List<String> referencedPackageNames = new ArrayList<>();
            for (int i = 1; i < genModel.getGenPackages().size(); i++) {
                GenPackage refPkg = genModel.getGenPackages().get(i);
                String pkgPath = refPkg.getBasePackage().replace('.', '/') + "/" + refPkg.getPackageName();
                referencedPackageNames.add(pkgPath);
                getLog().info("Will delete generated files for referenced package: " + pkgPath);
            }

            // Use OSGi templates if OSGi compatibility is enabled
            boolean useOsgiTemplates = genModel.isOSGiCompatible();
            Optional<String> result = runGenerator(genModel, ecoreFile.getAbsolutePath(), useOsgiTemplates);

            // Delete generated files for referenced packages (they come from dependency
            // JARs)
            if (!result.isPresent()) {
                File outputDir = new File(project.getBasedir(), outputDirectory);
                for (String pkgPath : referencedPackageNames) {
                    File pkgDir = new File(outputDir, pkgPath);
                    if (pkgDir.exists()) {
                        deleteDirectory(pkgDir);
                        getLog().info("Deleted generated files for referenced package: " + pkgDir.getAbsolutePath());
                    }
                }
            }

            return result;
        } finally {
            resourceSet.getResources().forEach(Resource::unload);
            resourceSet.getResources().clear();
        }
    }

    // ==================== Shared Methods ====================

    private void setupURIMapping(ResourceSet resourceSet, File baseDir, String projectName) {
        URI platformResourceURI = URI.createURI("platform:/resource/" + projectName + "/");
        URI fileURI = URI.createFileURI(baseDir.getAbsolutePath() + "/");
        resourceSet.getURIConverter().getURIMap().put(platformResourceURI, fileURI);
        org.eclipse.emf.ecore.resource.URIConverter.URI_MAP.put(platformResourceURI, fileURI);
        getLog().info("URI mapping: " + platformResourceURI + " -> " + fileURI);
    }

    private void setupDependencyURIMappings(ResourceSet resourceSet) {
        // Set up URI mappings for Maven dependencies that contain model files
        for (Artifact artifact : project.getArtifacts()) {
            File file = artifact.getFile();
            if (file != null && file.getName().endsWith(".jar")) {
                String artifactId = artifact.getArtifactId();
                try (JarFile jarFile = new JarFile(file)) {
                    // Check if the JAR contains model files
                    Enumeration<JarEntry> entries = jarFile.entries();
                    boolean hasModels = false;
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.endsWith(".genmodel") || name.endsWith(".ecore")) {
                            if (!name.contains("org/eclipse/emf/")) {
                                hasModels = true;
                                break;
                            }
                        }
                    }

                    if (hasModels) {
                        // Set up URI mapping: platform:/resource/{artifactId}/ -> jar:file:{jarPath}!/
                        URI platformURI = URI.createURI("platform:/resource/" + artifactId + "/");
                        URI jarURI = URI.createURI("jar:file:" + file.getAbsolutePath() + "!/");
                        resourceSet.getURIConverter().getURIMap().put(platformURI, jarURI);
                        org.eclipse.emf.ecore.resource.URIConverter.URI_MAP.put(platformURI, jarURI);
                        getLog().info("Dependency URI mapping: " + platformURI + " -> " + jarURI);
                    }
                } catch (IOException e) {
                    getLog().debug("Could not scan JAR for model files: " + file.getName());
                }
            }
        }
    }

    private Optional<String> runGenerator(GenModel genModel, String originalPath, boolean useOsgiTemplates) {
        Generator gen = new Generator();
        configureGenerator(gen, useOsgiTemplates);
        gen.setInput(genModel);

        Map<String, Object> props = new HashMap<>();

        // Determine genmodel folder - use configured location or default "model"
        String effectiveGenmodelFolder = genmodelIncludeLocation;
        if (effectiveGenmodelFolder == null || effectiveGenmodelFolder.isEmpty()) {
            // Default to "model" folder, matching where model files are copied
            if (genmodelTargetFile != null && !genmodelTargetFile.isEmpty()) {
                int lastSlash = genmodelTargetFile.lastIndexOf('/');
                effectiveGenmodelFolder = lastSlash > 0 ? genmodelTargetFile.substring(0, lastSlash) : "model";
            } else {
                effectiveGenmodelFolder = "model";
            }
        }
        props.put(INCLUDE_GEN_MODEL_FOLDER, effectiveGenmodelFolder);
        props.put(INCLUDE_ECORE_FOLDER, effectiveGenmodelFolder);
        getLog().info("Using genmodel folder: " + effectiveGenmodelFolder);
        getLog().info("Using ecore folder: " + effectiveGenmodelFolder);

        // Determine ecore bundle location
        String effectiveEcoreBundleLocation = ecoreBundleLocation;
        if (effectiveEcoreBundleLocation == null || effectiveEcoreBundleLocation.isEmpty()) {
            if (ecoreTargetFile != null && !ecoreTargetFile.isEmpty()) {
                effectiveEcoreBundleLocation = ecoreTargetFile;
            } else if (ecoreFile != null) {
                effectiveEcoreBundleLocation = "model/" + ecoreFile.getName();
            }
        }
        if (effectiveEcoreBundleLocation != null) {
            props.put(ECORE_BUNDLE_LOCATION, effectiveEcoreBundleLocation);
            getLog().info("Using ecore bundle location: " + effectiveEcoreBundleLocation);
        }

        gen.getOptions().data = new Object[] { props };

        genModel.setCanGenerate(true);
        genModel.setUpdateClasspath(false);

        getLog().info("Starting generator run");
        try {
            Diagnostic diagnostic = gen.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE,
                    CodeGenUtil.EclipseUtil.createMonitor(new MavenProgressMonitor(getLog()), 1));

            getLog().info("Generation diagnostic severity: " + diagnostic.getSeverity());
            printDiagnostic(diagnostic, "");

            if (diagnostic.getSeverity() == Diagnostic.ERROR) {
                return Optional.of("Code generation failed: " + diagnostic.toString());
            }
        } catch (Exception e) {
            String message = "Error during code generation: " + e.getMessage();
            getLog().error(message, e);
            return Optional.of(message);
        }

        return Optional.empty();
    }

    private void configureEMF(ResourceSet resourceSet) {
        GenModelPackageImpl.init();
        GenModelFactoryImpl.init();

        // Clear the genPackageRegistry for a fresh run
        genPackageRegistry.clear();

        resourceSet.getResourceFactoryRegistry().getContentTypeToFactoryMap().put(GenModelPackage.eCONTENT_TYPE,
                new XMIResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getContentTypeToFactoryMap().put("application/xmi",
                new XMIResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("genmodel",
                new XMIResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore",
                new EcoreResourceFactoryImpl());

        // Always load models from dependencies for both GenModel and Ecore modes
        loadModelsFromDependencies(resourceSet);
    }

    private void configureGenerator(Generator gen, boolean useOsgiTemplates) {
        // Always use the Fennec adapter factory for standalone (non-Eclipse) generation
        // The OSGi-specific code generation is controlled by
        // GenModel.isOSGiCompatible()
        gen.getAdapterFactoryDescriptorRegistry().addDescriptor(GenModelPackage.eNS_URI,
                BNDGeneratorAdapterFactory.DESCRIPTOR);

        if (useOsgiTemplates) {
            getLog().info("Using Fennec templates with OSGi service components");
        } else {
            getLog().info("Using Fennec templates (standard EMF output, no OSGi service components)");
        }
    }

    /**
     * Adds usedGenPackages to the GenModel for external packages referenced by the
     * model. This enables resolution of cross-package references during code
     * generation.
     */
    private void addUsedGenPackagesFromDependencies(GenModel genModel) {
        // Collect all nsURIs of packages already in the GenModel
        Set<String> ownPackageNsURIs = new HashSet<>();
        for (GenPackage genPackage : genModel.getGenPackages()) {
            EPackage ePackage = genPackage.getEcorePackage();
            if (ePackage != null && ePackage.getNsURI() != null) {
                ownPackageNsURIs.add(ePackage.getNsURI());
            }
        }

        // Find all referenced external packages
        Set<String> referencedNsURIs = new HashSet<>();
        for (GenPackage genPackage : genModel.getGenPackages()) {
            EPackage ePackage = genPackage.getEcorePackage();
            if (ePackage != null) {
                collectReferencedExternalNsURIs(ePackage, ownPackageNsURIs, referencedNsURIs);
            }
        }

        // Add GenPackages from the registry for all referenced external packages
        for (String nsURI : referencedNsURIs) {
            GenPackage externalGenPackage = genPackageRegistry.get(nsURI);
            if (externalGenPackage != null) {
                if (!genModel.getUsedGenPackages().contains(externalGenPackage)) {
                    genModel.getUsedGenPackages().add(externalGenPackage);
                    getLog().info("Added usedGenPackage for external reference: " + externalGenPackage.getPackageName()
                            + " (" + nsURI + ")");
                }
            } else {
                getLog().warn("No GenPackage found for referenced external package: " + nsURI);
            }
        }
    }

    /**
     * Collects nsURIs of external packages referenced by the given EPackage.
     */
    private void collectReferencedExternalNsURIs(EPackage ePackage, Set<String> ownPackageNsURIs,
            Set<String> referencedNsURIs) {
        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                // Check super types
                for (EClass superClass : eClass.getESuperTypes()) {
                    addExternalNsURI(superClass.getEPackage(), ownPackageNsURIs, referencedNsURIs);
                }

                // Check structural features
                for (EStructuralFeature feature : eClass.getEStructuralFeatures()) {
                    EClassifier featureType = feature.getEType();
                    if (featureType != null) {
                        addExternalNsURI(featureType.getEPackage(), ownPackageNsURIs, referencedNsURIs);
                    }
                }
            }
        }
    }

    /**
     * Adds the nsURI of the given package if it's an external (non-EMF core)
     * package.
     */
    private void addExternalNsURI(EPackage ePackage, Set<String> ownPackageNsURIs, Set<String> referencedNsURIs) {
        if (ePackage != null) {
            String nsURI = ePackage.getNsURI();
            if (nsURI != null && !ownPackageNsURIs.contains(nsURI) && !isEMFCorePackage(nsURI)) {
                referencedNsURIs.add(nsURI);
            }
        }
    }

    // ==================== GenModel Creation (Ecore mode) ====================

    private GenModel createGenModel(EPackage ePackage, String projectName, ResourceSet resourceSet) {
        GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();

        logGenModelAnnotations(ePackage);

        String modelDirectory = "/" + projectName + (outputDirectory.startsWith("/") ? "" : "/") + outputDirectory;
        genModel.setModelDirectory(modelDirectory);
        genModel.setModelName(capitalize(ePackage.getName()));
        genModel.setComplianceLevel(org.eclipse.emf.codegen.ecore.genmodel.GenJDKLevel.JDK170_LITERAL);

        // OSGi compatibility
        boolean effectiveOsgiCompatible = osgiCompatible;
        String osgiAnnotation = getGenModelAnnotation(ePackage, "oSGiCompatible");
        if (osgiAnnotation != null) {
            effectiveOsgiCompatible = Boolean.parseBoolean(osgiAnnotation);
            getLog().info("Using oSGiCompatible from Ecore annotation: " + effectiveOsgiCompatible);
        }
        if (effectiveOsgiCompatible) {
            genModel.setOSGiCompatible(true);
            genModel.setOperationReflection(true);
        }

        // Boolean options
        genModel.setSuppressInterfaces(
                suppressInterfaces || getGenModelAnnotationBoolean(ePackage, "suppressInterfaces", false));
        genModel.setSuppressEMFTypes(
                suppressEMFTypes || getGenModelAnnotationBoolean(ePackage, "suppressEMFTypes", false));
        genModel.setSuppressEMFMetaData(
                suppressEMFMetaData || getGenModelAnnotationBoolean(ePackage, "suppressEMFMetaData", false));
        genModel.setSuppressGenModelAnnotations(suppressGenModelAnnotations
                || getGenModelAnnotationBoolean(ePackage, "suppressGenModelAnnotations", false));
        genModel.setPublicConstructors(
                publicConstructors || getGenModelAnnotationBoolean(ePackage, "publicConstructors", false));

        // String options with fallback to annotations
        applyStringOption(genModel, ePackage, "rootExtendsClass", rootExtendsClass, genModel::setRootExtendsClass);
        applyStringOption(genModel, ePackage, "rootExtendsInterface", rootExtendsInterface,
                genModel::setRootExtendsInterface);
        applyStringOption(genModel, ePackage, "copyrightText", copyrightText, genModel::setCopyrightText);

        // Resolve all proxies in the EPackage to ensure we get the correct EPackage
        // instances
        // from the registry (for referenced packages like bi and cg)
        EcoreUtil.resolveAll(ePackage);

        // Find referenced external packages
        Set<EPackage> referencedPackages = findReferencedExternalPackages(ePackage);

        // Initialize GenModel with ALL packages (main + referenced) in the same
        // GenModel
        // This is required because findGenClassifier needs all packages in the same
        // GenModel
        // to correctly resolve type references for code generation
        List<EPackage> allPackages = new ArrayList<>();
        allPackages.add(ePackage);
        allPackages.addAll(referencedPackages);
        genModel.initialize(allPackages);

        // Configure referenced GenPackages (indices 1+) so they don't generate code
        // but are still available for findGenClassifier
        for (int i = 1; i < genModel.getGenPackages().size(); i++) {
            GenPackage refGenPackage = genModel.getGenPackages().get(i);
            EPackage refEPackage = refGenPackage.getEcorePackage();
            configureGenPackageFromAnnotations(refGenPackage, refEPackage);
            getLog().info("Configured referenced GenPackage (no code gen): " + refGenPackage.getPackageName() + " ("
                    + refEPackage.getNsURI() + ")");
        }

        // Resolve all references in the GenModel
        EcoreUtil.resolveAll(genModel);

        // Create resource for GenModel with a synthetic URI
        // (not a platform URI that would map to the source model folder)
        URI genModelUri = URI.createURI("synthetic:/" + ePackage.getName() + "-main.genmodel");
        Resource genModelResource = resourceSet.createResource(genModelUri);
        genModelResource.getContents().add(genModel);

        // Configure main GenPackage
        if (!genModel.getGenPackages().isEmpty()) {
            GenPackage mainGenPackage = genModel.getGenPackages().get(0);

            // basePackage
            String effectiveBasePackage = getEffectiveValue(basePackage,
                    () -> getGenModelAnnotation(ePackage, "basePackage"), () -> deriveBasePackage(ePackage.getNsURI()));
            if (effectiveBasePackage != null && !effectiveBasePackage.isEmpty()) {
                mainGenPackage.setBasePackage(effectiveBasePackage);
                getLog().info("Using basePackage: " + effectiveBasePackage);
            }

            // prefix
            String effectivePrefix = getEffectiveValue(prefix, () -> getGenModelAnnotation(ePackage, "prefix"),
                    () -> capitalize(ePackage.getName()));
            mainGenPackage.setPrefix(effectivePrefix);
            getLog().info("Using prefix: " + effectivePrefix);

            // fileExtension
            String effectiveFileExtension = getEffectiveValue(fileExtension,
                    () -> getGenModelAnnotation(ePackage, "fileExtensions"),
                    () -> getGenModelAnnotation(ePackage, "fileExtension"));
            if (effectiveFileExtension != null && !effectiveFileExtension.isEmpty()) {
                mainGenPackage.setFileExtensions(effectiveFileExtension);
                getLog().info("Using fileExtension: " + effectiveFileExtension);
            }

            // resource type (XMI, XML, BASIC, NONE)
            String effectiveResource = getEffectiveValue(resource, () -> getGenModelAnnotation(ePackage, "resource"));
            if (effectiveResource != null && !effectiveResource.isEmpty()) {
                org.eclipse.emf.codegen.ecore.genmodel.GenResourceKind resourceKind = org.eclipse.emf.codegen.ecore.genmodel.GenResourceKind
                        .get(effectiveResource);
                if (resourceKind != null) {
                    mainGenPackage.setResource(resourceKind);
                    getLog().info("Using resource: " + effectiveResource);
                }
            }

            mainGenPackage.setEcorePackage(ePackage);
        }

        return genModel;
    }

    private void applyStringOption(GenModel genModel, EPackage ePackage, String key, String mavenValue,
            java.util.function.Consumer<String> setter) {
        String effective = mavenValue;
        if (effective == null || effective.isEmpty()) {
            effective = getGenModelAnnotation(ePackage, key);
        }
        if (effective != null && !effective.isEmpty()) {
            setter.accept(effective);
            getLog().info("Using " + key + ": " + (key.contains("copyright") ? "(from annotation)" : effective));
        }
    }

    @SafeVarargs
    private final String getEffectiveValue(String primary, java.util.function.Supplier<String>... fallbacks) {
        if (primary != null && !primary.isEmpty()) {
            return primary;
        }
        for (java.util.function.Supplier<String> fallback : fallbacks) {
            String value = fallback.get();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    // ==================== Helper Methods ====================

    private void logGenModelInfo(GenModel genModel) {
        getLog().info("Created GenModel:");
        getLog().info("  Model directory: " + genModel.getModelDirectory());
        getLog().info("  Model name: " + genModel.getModelName());
        getLog().info("  GenModel resource: " + genModel.eResource().getURI());
        getLog().info("  GenPackages count: " + genModel.getGenPackages().size());
        for (GenPackage gp : genModel.getGenPackages()) {
            getLog().info("  GenPackage: " + gp.getPackageName() + ", basePackage: " + gp.getBasePackage());
        }
    }

    private void logGenModelAnnotations(EPackage ePackage) {
        EAnnotation annotation = ePackage.getEAnnotation(GENMODEL_ANNOTATION_SOURCE);
        if (annotation != null && !annotation.getDetails().isEmpty()) {
            getLog().info("Found GenModel annotations in EPackage '" + ePackage.getName() + "':");
            for (Map.Entry<String, String> entry : annotation.getDetails()) {
                getLog().info("  " + entry.getKey() + " = " + entry.getValue());
            }
        }
    }

    private String getGenModelAnnotation(EPackage ePackage, String key) {
        EAnnotation annotation = ePackage.getEAnnotation(GENMODEL_ANNOTATION_SOURCE);
        if (annotation != null) {
            String value = annotation.getDetails().get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private boolean getGenModelAnnotationBoolean(EPackage ePackage, String key, boolean defaultValue) {
        String value = getGenModelAnnotation(ePackage, key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    private Set<EPackage> findReferencedExternalPackages(EPackage ePackage) {
        Set<EPackage> referenced = new HashSet<>();
        String mainNsURI = ePackage.getNsURI();

        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                for (EClass superClass : eClass.getESuperTypes()) {
                    EPackage superPackage = superClass.getEPackage();
                    if (superPackage != null && !mainNsURI.equals(superPackage.getNsURI())
                            && !isEMFCorePackage(superPackage.getNsURI())) {
                        referenced.add(superPackage);
                    }
                }

                for (EStructuralFeature feature : eClass.getEStructuralFeatures()) {
                    EClassifier featureType = feature.getEType();
                    if (featureType != null) {
                        EPackage featurePackage = featureType.getEPackage();
                        if (featurePackage != null && !mainNsURI.equals(featurePackage.getNsURI())
                                && !isEMFCorePackage(featurePackage.getNsURI())) {
                            referenced.add(featurePackage);
                        }
                    }
                }
            }
        }
        return referenced;
    }

    private boolean isEMFCorePackage(String nsURI) {
        return nsURI == null || nsURI.startsWith("http://www.eclipse.org/emf/")
                || nsURI.startsWith("http://www.w3.org/") || nsURI.startsWith("http:///org/eclipse/emf/");
    }

    private String deriveBasePackage(String nsUri) {
        if (nsUri == null || nsUri.isEmpty()) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(nsUri);
            String host = uri.getHost();
            String path = uri.getPath();

            if (host == null) {
                return null;
            }

            String[] hostParts = host.split("\\.");
            StringBuilder sb = new StringBuilder();
            for (int i = hostParts.length - 1; i >= 0; i--) {
                if (sb.length() > 0) {
                    sb.append(".");
                }
                sb.append(hostParts[i].toLowerCase());
            }

            if (path != null && !path.isEmpty()) {
                String[] pathParts = path.split("/");
                for (int i = 1; i < pathParts.length - 1; i++) {
                    if (!pathParts[i].isEmpty()) {
                        sb.append(".").append(pathParts[i].toLowerCase());
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            getLog().warn("Could not derive base package from nsURI: " + nsUri);
            return null;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Configures a GenPackage from EPackage GenModel annotations. Sets basePackage,
     * prefix, and fileExtensions based on annotations or derived values.
     */
    private void configureGenPackageFromAnnotations(GenPackage genPackage, EPackage ePackage) {
        // basePackage
        String basePackage = getGenModelAnnotation(ePackage, "basePackage");
        if (basePackage == null || basePackage.isEmpty()) {
            basePackage = deriveBasePackage(ePackage.getNsURI());
        }
        if (basePackage != null && !basePackage.isEmpty()) {
            genPackage.setBasePackage(basePackage);
        }

        // prefix
        String prefix = getGenModelAnnotation(ePackage, "prefix");
        if (prefix == null || prefix.isEmpty()) {
            prefix = capitalize(ePackage.getName());
        }
        genPackage.setPrefix(prefix);

        // fileExtensions
        String fileExt = getGenModelAnnotation(ePackage, "fileExtensions");
        if (fileExt != null && !fileExt.isEmpty()) {
            genPackage.setFileExtensions(fileExt);
        }
    }

    /**
     * Registers an EPackage in the registries and creates a corresponding
     * GenPackage.
     *
     * @param resourceSet the resource set
     * @param ePackage    the EPackage to register
     * @param source      description of where the package came from (for logging)
     */
    private void registerEPackage(ResourceSet resourceSet, EPackage ePackage, String source) {
        String nsURI = ePackage.getNsURI();
        if (nsURI == null || EPackage.Registry.INSTANCE.containsKey(nsURI)) {
            return;
        }

        EPackage.Registry.INSTANCE.put(nsURI, ePackage);
        resourceSet.getPackageRegistry().put(nsURI, ePackage);
        getLog().info("Registered EPackage" + source + ": " + ePackage.getName() + " (" + nsURI + ")");

        // Also create and register a GenPackage if GenModel annotations are present
        if (!genPackageRegistry.containsKey(nsURI)) {
            GenPackage genPackage = createGenPackageFromEcore(resourceSet, ePackage);
            if (genPackage != null) {
                genPackageRegistry.put(nsURI, genPackage);
                getLog().info("Created GenPackage" + source + ": " + genPackage.getPackageName() + " (" + nsURI + ")");
            }
        }
    }

    private void loadModelsFromDependencies(ResourceSet resourceSet) {
        // Build a map of reactor projects by groupId:artifactId for quick lookup
        Map<String, MavenProject> reactorProjectMap = new HashMap<>();
        if (reactorProjects != null) {
            for (MavenProject reactorProject : reactorProjects) {
                String key = reactorProject.getGroupId() + ":" + reactorProject.getArtifactId();
                reactorProjectMap.put(key, reactorProject);
            }
        }

        // Collect model files from all dependencies
        List<File> ecoreFiles = new ArrayList<>();
        List<File> genmodelFiles = new ArrayList<>();

        for (Artifact artifact : project.getArtifacts()) {
            String key = artifact.getGroupId() + ":" + artifact.getArtifactId();
            MavenProject reactorProject = reactorProjectMap.get(key);

            if (reactorProject != null) {
                // Reactor dependency: scan source directories
                collectModelFilesFromReactorProject(reactorProject, ecoreFiles, genmodelFiles);
            } else {
                // External JAR dependency
                File file = artifact.getFile();
                if (file != null && file.exists() && file.getName().endsWith(".jar")) {
                    collectModelFilesFromJar(file, resourceSet, ecoreFiles, genmodelFiles);
                }
            }
        }

        // Load ecore files first (to register EPackages)
        for (File ecoreFile : ecoreFiles) {
            loadEcoreFromFile(resourceSet, ecoreFile);
        }

        // Then load genmodel files (which reference the EPackages)
        for (File genmodelFile : genmodelFiles) {
            loadGenModelFromFile(resourceSet, genmodelFile);
        }
    }

    /**
     * Collect model files from a reactor project's resource directories and model
     * folder.
     */
    private void collectModelFilesFromReactorProject(MavenProject reactorProject, List<File> ecoreFiles,
            List<File> genmodelFiles) {
        // Check Maven resource directories
        for (org.apache.maven.model.Resource resource : reactorProject.getResources()) {
            File resourceDir = new File(resource.getDirectory());
            if (resourceDir.exists() && resourceDir.isDirectory()) {
                collectModelFilesFromDirectory(resourceDir, ecoreFiles, genmodelFiles);
            }
        }

        // Also check model/ directory at project root (common location for model files)
        File modelDir = new File(reactorProject.getBasedir(), "model");
        if (modelDir.exists() && modelDir.isDirectory()) {
            collectModelFilesFromDirectory(modelDir, ecoreFiles, genmodelFiles);
        }
    }

    /**
     * Collect model files from a JAR dependency.
     */
    private void collectModelFilesFromJar(File jarFile, ResourceSet resourceSet, List<File> ecoreFiles,
            List<File> genmodelFiles) {
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (isModelPath(name)) {
                    if (name.endsWith(".ecore")) {
                        loadEcoreFromJar(resourceSet, jarFile, name);
                    } else if (name.endsWith(".genmodel")) {
                        loadGenModelFromJar(resourceSet, jarFile, name);
                    }
                }
            }
        } catch (IOException e) {
            getLog().debug("Could not scan JAR for model files: " + jarFile.getName());
        }
    }

    /**
     * Recursively collect model files from a directory.
     */
    private void collectModelFilesFromDirectory(File dir, List<File> ecoreFiles, List<File> genmodelFiles) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectModelFilesFromDirectory(file, ecoreFiles, genmodelFiles);
            } else if (isModelPath(file.getAbsolutePath())) {
                if (file.getName().endsWith(".ecore")) {
                    ecoreFiles.add(file);
                } else if (file.getName().endsWith(".genmodel")) {
                    genmodelFiles.add(file);
                }
            }
        }
    }

    /**
     * Check if a path should be processed (excludes EMF internal paths).
     */
    private boolean isModelPath(String path) {
        return !path.contains("org/eclipse/emf/ecore/") && !path.contains("org/eclipse/emf/codegen/")
                && !path.contains("META-INF/");
    }

    private void loadEcoreFromFile(ResourceSet resourceSet, File ecoreFile) {
        try {
            URI ecoreUri = URI.createFileURI(ecoreFile.getAbsolutePath());
            Resource ecoreResource = resourceSet.getResource(ecoreUri, true);
            if (ecoreResource != null && !ecoreResource.getContents().isEmpty()) {
                for (org.eclipse.emf.ecore.EObject obj : ecoreResource.getContents()) {
                    if (obj instanceof EPackage ePackage) {
                        registerEPackage(resourceSet, ePackage, "");
                    }
                }
            }
        } catch (Exception e) {
            getLog().debug("Could not load Ecore: " + ecoreFile.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    /**
     * Saves the GenModel to target/classes for inclusion in the JAR. The genmodel
     * is generated on-the-fly and NOT stored in the source model directory. Updates
     * ecore references to use correct relative paths within the JAR.
     */
    private void saveGenModelToResources(GenModel genModel, EPackage ePackage) {
        try {
            // Determine the target path in target/classes/model
            File baseDir = project.getBasedir();
            File targetModelDir = new File(baseDir, "target/classes/model");
            targetModelDir.mkdirs();

            String genModelFileName = ePackage.getName() + ".genmodel";
            File genModelFile = new File(targetModelDir, genModelFileName);

            // First, copy the ecore file to target/classes/model so relative paths work
            String ecoreFileName = ecoreFile.getName();
            File targetEcoreFile = new File(targetModelDir, ecoreFileName);
            if (!targetEcoreFile.exists()) {
                java.nio.file.Files.copy(ecoreFile.toPath(), targetEcoreFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // Update the EPackage's resource URI to point to target/classes/model
            // This ensures the genmodel will have correct relative paths (e.g.,
            // "model.ecore")
            Resource ecoreResource = ePackage.eResource();
            if (ecoreResource != null) {
                URI targetEcoreUri = URI.createFileURI(targetEcoreFile.getAbsolutePath());
                ecoreResource.setURI(targetEcoreUri);
            }

            // Create the resource and save
            URI genModelUri = URI.createFileURI(genModelFile.getAbsolutePath());
            Resource genModelResource = genModel.eResource();

            // If the genmodel is in a synthetic resource, we need to move it to the file
            // resource
            if (genModelResource.getURI().toString().startsWith("synthetic:")
                    || genModelResource.getURI().toString().startsWith("platform:")) {
                ResourceSet resourceSet = genModelResource.getResourceSet();
                Resource fileResource = resourceSet.createResource(genModelUri);
                fileResource.getContents().add(genModel);
                genModelResource = fileResource;
            } else {
                genModelResource.setURI(genModelUri);
            }

            genModelResource.save(Collections.emptyMap());
            getLog().info("Saved GenModel to target/classes: " + genModelFile.getAbsolutePath());
        } catch (IOException e) {
            getLog().warn("Could not save GenModel to target/classes: " + e.getMessage());
        }
    }

    /**
     * Copies model files (ecore) to target/classes for JAR inclusion. GenModel
     * files are generated on-the-fly directly to target/classes.
     *
     * Target file paths can be configured with ecoreTargetFile. If not set,
     * defaults to "model/{filename}".
     */
    private void copyModelFilesToTarget() throws MojoExecutionException {
        File baseDir = project.getBasedir();
        File targetClassesDir = new File(baseDir, "target/classes");

        // Determine the model directory (from ecore or genmodel location)
        File modelDir = null;
        if (ecoreFile != null && ecoreFile.exists()) {
            modelDir = ecoreFile.getParentFile();
        } else if (genmodelFile != null && genmodelFile.exists()) {
            modelDir = genmodelFile.getParentFile();
        }

        if (modelDir == null || !modelDir.exists()) {
            getLog().warn("Could not determine model directory. Skipping model file copying.");
            return;
        }

        // Copy all ecore files from the model directory
        File[] ecoreFiles = modelDir.listFiles((dir, name) -> name.endsWith(".ecore"));
        if (ecoreFiles != null) {
            for (File ef : ecoreFiles) {
                String targetPath = (ecoreTargetFile != null && !ecoreTargetFile.isEmpty()) ? ecoreTargetFile
                        : "model/" + ef.getName();
                File target = new File(targetClassesDir, targetPath);
                target.getParentFile().mkdirs();
                copyFile(ef, target, targetPath);
            }
        }

        // In genmodel mode, copy genmodel files from the source model directory
        // In ecore mode, genmodel is generated directly to target/classes
        if (genmodelFile != null && genmodelFile.exists()) {
            File[] genmodelFiles = modelDir.listFiles((dir, name) -> name.endsWith(".genmodel"));
            if (genmodelFiles != null) {
                for (File gm : genmodelFiles) {
                    String targetPath = (genmodelTargetFile != null && !genmodelTargetFile.isEmpty())
                            ? genmodelTargetFile
                            : "model/" + gm.getName();
                    File target = new File(targetClassesDir, targetPath);
                    target.getParentFile().mkdirs();
                    copyFile(gm, target, targetPath);
                }
            }
        }
    }

    /**
     * Copies a single file to the target location.
     */
    private void copyFile(File source, File target, String targetPath) throws MojoExecutionException {
        try {
            java.nio.file.Files.copy(source.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            getLog().info("Copied " + source.getName() + " to " + targetPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to copy " + source + " to " + target, e);
        }
    }

    /**
     * Post-processes generated source files to remove source location attributes
     * from @EPackage annotations. Keeps uri, genModel, and ecore but removes
     * genModelSourceLocations and ecoreSourceLocations.
     */
    private void postProcessEPackageAnnotations(File outputDir) throws MojoExecutionException {
        getLog().info("Post-processing @EPackage annotations to remove source locations...");

        try {
            java.nio.file.Files.walk(outputDir.toPath()).filter(p -> p.toString().endsWith("Package.java"))
                    .forEach(this::removeSourceLocationsFromPackageFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to post-process generated sources", e);
        }
    }

    /**
     * Removes genModelSourceLocations and ecoreSourceLocations attributes from
     *
     * @EPackage annotations in a single file.
     */
    private void removeSourceLocationsFromPackageFile(java.nio.file.Path file) {
        try {
            String content = java.nio.file.Files.readString(file);
            String modified = content;

            // Remove genModelSourceLocations = {...}
            modified = modified.replaceAll(",\\s*genModelSourceLocations\\s*=\\s*\\{[^}]*\\}", "");

            // Remove ecoreSourceLocations = "..."
            modified = modified.replaceAll(",\\s*ecoreSourceLocations\\s*=\\s*\"[^\"]*\"", "");

            if (!content.equals(modified)) {
                java.nio.file.Files.writeString(file, modified);
                getLog().info("Removed source locations from @EPackage in: " + file.getFileName());
            }
        } catch (IOException e) {
            getLog().warn("Could not process file: " + file + " - " + e.getMessage());
        }
    }

    /**
     * Creates a GenPackage from an EPackage using GenModel annotations.
     */
    private GenPackage createGenPackageFromEcore(ResourceSet resourceSet, EPackage ePackage) {
        try {
            GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
            genModel.initialize(Collections.singletonList(ePackage));
            genModel.setModelName(capitalize(ePackage.getName()));

            // Create a synthetic resource for the GenModel
            URI genModelUri = URI.createURI("synthetic:/" + ePackage.getName() + ".genmodel");
            Resource genModelResource = resourceSet.createResource(genModelUri);
            genModelResource.getContents().add(genModel);

            if (!genModel.getGenPackages().isEmpty()) {
                GenPackage genPackage = genModel.getGenPackages().get(0);
                configureGenPackageFromAnnotations(genPackage, ePackage);
                genPackage.setEcorePackage(ePackage);
                EcoreUtil.resolveAll(genModel);
                return genPackage;
            }
        } catch (Exception e) {
            getLog().debug("Could not create GenPackage from Ecore: " + ePackage.getName() + " - " + e.getMessage());
        }
        return null;
    }

    private void loadGenModelFromFile(ResourceSet resourceSet, File genmodelFile) {
        try {
            URI genmodelUri = URI.createFileURI(genmodelFile.getAbsolutePath());
            Resource genmodelResource = resourceSet.getResource(genmodelUri, true);
            if (genmodelResource != null && !genmodelResource.getContents().isEmpty()) {
                for (org.eclipse.emf.ecore.EObject obj : genmodelResource.getContents()) {
                    if (obj instanceof GenModel genModel) {
                        EcoreUtil.resolveAll(genModel);
                        for (GenPackage genPackage : genModel.getGenPackages()) {
                            EPackage ePackage = genPackage.getEcorePackage();
                            if (ePackage != null && ePackage.getNsURI() != null) {
                                genPackageRegistry.put(ePackage.getNsURI(), genPackage);
                                getLog().info("Registered GenPackage: " + genPackage.getPackageName() + " ("
                                        + ePackage.getNsURI() + ")");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLog().debug("Could not load GenModel: " + genmodelFile.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    private void loadEcoreFromJar(ResourceSet resourceSet, File jarFile, String ecorePath) {
        try {
            URI ecoreUri = URI.createURI("jar:file:" + jarFile.getAbsolutePath() + "!/" + ecorePath);
            Resource ecoreResource = resourceSet.getResource(ecoreUri, true);
            if (ecoreResource != null && !ecoreResource.getContents().isEmpty()) {
                for (org.eclipse.emf.ecore.EObject obj : ecoreResource.getContents()) {
                    if (obj instanceof EPackage ePackage) {
                        registerEPackage(resourceSet, ePackage, " from JAR");
                    }
                }
            }
        } catch (Exception e) {
            getLog().debug("Could not load Ecore from JAR: " + jarFile.getName() + "!/" + ecorePath);
        }
    }

    private void loadGenModelFromJar(ResourceSet resourceSet, File jarFile, String genmodelPath) {
        try {
            URI genmodelUri = URI.createURI("jar:file:" + jarFile.getAbsolutePath() + "!/" + genmodelPath);
            Resource genmodelResource = resourceSet.getResource(genmodelUri, true);
            if (genmodelResource != null && !genmodelResource.getContents().isEmpty()) {
                for (org.eclipse.emf.ecore.EObject obj : genmodelResource.getContents()) {
                    if (obj instanceof GenModel genModel) {
                        EcoreUtil.resolveAll(genModel);
                        for (GenPackage genPackage : genModel.getGenPackages()) {
                            EPackage ePackage = genPackage.getEcorePackage();
                            if (ePackage != null && ePackage.getNsURI() != null) {
                                genPackageRegistry.put(ePackage.getNsURI(), genPackage);
                                getLog().info("Registered GenPackage: " + genPackage.getPackageName() + " ("
                                        + ePackage.getNsURI() + ")");
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLog().debug("Could not load GenModel from JAR: " + jarFile.getName() + "!/" + genmodelPath);
        }
    }

    private void printDiagnostic(Diagnostic diagnostic, String prefix) {
        if (diagnostic.getSeverity() != Diagnostic.OK) {
            if (diagnostic.getSeverity() == Diagnostic.ERROR) {
                getLog().error(prefix + diagnostic.getMessage() + " - " + diagnostic.getSource());
            } else {
                getLog().warn(prefix + diagnostic.getMessage() + " - " + diagnostic.getSource());
            }
            if (diagnostic.getException() != null) {
                getLog().error(prefix + "Exception: ", diagnostic.getException());
            }
        }
        for (Diagnostic child : diagnostic.getChildren()) {
            printDiagnostic(child, prefix + "  ");
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
