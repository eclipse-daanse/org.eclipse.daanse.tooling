# Eclipse Daanse EMF Code Generator Maven Plugin

A Maven plugin for generating OSGi-compatible EMF model code from Ecore or GenModel files.

## Features

- Generate EMF model code from `.ecore` files (no GenModel required)
- Generate EMF model code from existing `.genmodel` files
- Automatic cross-package reference resolution from Maven dependencies
- OSGi-compatible code generation via Fennec EMF templates
- GenModel annotations support in Ecore files
- Multi-module project support with proper dependency handling

## Usage

Add the plugin to your `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.tooling.emf.codegen.maven</artifactId>
      <version>0.0.1-SNAPSHOT</version>
      <executions>
        <execution>
          <goals>
            <goal>generate</goal>
          </goals>
          <configuration>
            <!-- See configuration options below -->
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

## Modes of Operation

### 1. Ecore Mode (Recommended)

Generate code directly from an Ecore file. GenModel settings can be provided via:
- Maven plugin configuration
- GenModel annotations in the Ecore file

```xml
<configuration>
  <ecoreFile>model/mymodel.ecore</ecoreFile>
  <outputDirectory>target/generated-sources/emf</outputDirectory>
</configuration>
```

### 2. GenModel Mode

Use an existing GenModel file:

```xml
<configuration>
  <genmodelFile>model/mymodel.genmodel</genmodelFile>
  <outputDirectory>target/generated-sources/emf</outputDirectory>
</configuration>
```

## Configuration Parameters

### Input Files

| Parameter | Property | Description |
|-----------|----------|-------------|
| `ecoreFile` | `emf.ecoreFile` | Path to the Ecore file |
| `genmodelFile` | `emf.genmodelFile` | Path to the GenModel file (takes precedence if both are set) |

### Output

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `outputDirectory` | `emf.outputDirectory` | `target/generated-sources/emf` | Output directory for generated code |
| `includeGenModelInJar` | `emf.includeGenModelInJar` | `false` | Include generated GenModel in JAR resources |

### GenModel Settings (Ecore Mode)

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| `basePackage` | `emf.basePackage` | (derived) | Base package for generated code |
| `prefix` | `emf.prefix` | (derived) | Prefix for generated class names |
| `fileExtension` | `emf.fileExtension` | - | File extension for model resources |
| `osgiCompatible` | `emf.osgiCompatible` | `true` | Generate OSGi-compatible code |
| `suppressInterfaces` | `emf.suppressInterfaces` | `false` | Suppress interface generation |
| `suppressEMFTypes` | `emf.suppressEMFTypes` | `false` | Use Java native types instead of EMF types |
| `suppressEMFMetaData` | `emf.suppressEMFMetaData` | `false` | Suppress EMF metadata generation |
| `suppressGenModelAnnotations` | `emf.suppressGenModelAnnotations` | `false` | Suppress GenModel annotations in generated code |
| `publicConstructors` | `emf.publicConstructors` | `false` | Make constructors public |
| `rootExtendsClass` | `emf.rootExtendsClass` | - | Root class for generated model objects |
| `rootExtendsInterface` | `emf.rootExtendsInterface` | - | Root interface for generated model objects |
| `copyrightText` | `emf.copyrightText` | - | Copyright text for generated files |

## GenModel Annotations in Ecore

You can embed GenModel settings directly in your Ecore file using annotations:

```xml
<ecore:EPackage xmi:version="2.0"
    xmlns:xmi="http://www.omg.org/XMI"
    xmlns:ecore="http://www.eclipse.org/emf/2002/Ecore"
    name="mymodel"
    nsURI="http://example.com/mymodel"
    nsPrefix="mymodel">

  <eAnnotations source="http://www.eclipse.org/emf/2002/GenModel">
    <details key="basePackage" value="com.example"/>
    <details key="prefix" value="MyModel"/>
    <details key="fileExtensions" value="mymodel"/>
    <details key="oSGiCompatible" value="true"/>
  </eAnnotations>

  <!-- EClasses, EDataTypes, etc. -->
</ecore:EPackage>
```

### Supported Annotation Keys

- `basePackage` - Base package for generated code
- `prefix` - Prefix for factory and package classes
- `fileExtensions` - File extensions for resources
- `oSGiCompatible` - Enable OSGi compatibility (true/false)
- `suppressInterfaces` - Suppress interface generation
- `suppressEMFTypes` - Use Java types instead of EMF types
- `copyrightText` - Copyright header text

## Cross-Package References

The plugin automatically resolves cross-package references from Maven dependencies. When your Ecore model references types from another package:

1. Add the dependency module to your `pom.xml`
2. The plugin scans JAR dependencies for `.ecore` and `.genmodel` files
3. GenPackages are automatically created and configured
4. Referenced packages are not regenerated; existing code from JARs is used

### Example Multi-Module Setup

```text
parent/
├── pom.xml
├── model-base/          # Independent model
│   ├── pom.xml
│   └── model/base.ecore
├── model-ext/           # Extends base model
│   ├── pom.xml
│   └── model/ext.ecore  # References base types
```

In `model-ext/pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>com.example</groupId>
    <artifactId>model-base</artifactId>
    <version>${project.version}</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.eclipse.daanse</groupId>
      <artifactId>org.eclipse.daanse.tooling.emf.codegen.maven</artifactId>
      <configuration>
        <ecoreFile>model/ext.ecore</ecoreFile>
        <!-- Only generates ext package; base is referenced from JAR -->
      </configuration>
    </plugin>
  </plugins>
</build>
```

## Required Dependencies

The plugin requires `biz.aQute.bndlib` for OSGi manifest generation
because the code uses bnd annotations for osgi support.

```xml
<plugin>
  <groupId>org.eclipse.daanse</groupId>
  <artifactId>org.eclipse.daanse.tooling.emf.codegen.maven</artifactId>
  <dependencies>
    <dependency>
      <groupId>biz.aQute.bnd</groupId>
      <artifactId>biz.aQute.bndlib</artifactId>
      <version>7.1.0</version>
    </dependency>
  </dependencies>
</plugin>
```

## Generated Code Structure

For a package named `mymodel` with `basePackage=com.example`:

```text
target/generated-sources/emf/
└── com/example/mymodel/
    ├── MymodelPackage.java          # EPackage interface
    ├── MymodelFactory.java          # EFactory interface
    ├── MyClass.java                 # EClass interfaces
    ├── impl/
    │   ├── MymodelPackageImpl.java  # EPackage implementation
    │   ├── MymodelFactoryImpl.java  # EFactory implementation
    │   └── MyClassImpl.java         # EClass implementations
    ├── util/
    │   ├── MymodelSwitch.java       # Type switch
    │   ├── MymodelAdapterFactory.java
    │   ├── MymodelResourceImpl.java
    │   ├── MymodelResourceFactoryImpl.java
    │   ├── MymodelValidator.java
    │   └── MymodelXMLProcessor.java
    └── configuration/               # OSGi components (if osgiCompatible=true)
        ├── MymodelEPackageConfigurator.java
        └── MymodelConfigurationComponent.java
```

