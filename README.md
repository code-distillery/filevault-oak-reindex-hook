# Jackrabbit File Vault InstallHook for  Oak Indexes

The module contains an InstallHook implementation for content-packages
that contain index definitions.

It helps to trigger a reindex when necessary but not otherwise, when
a package containing Oak index definitions is installed.

#Usage

To use this index hook:

* create a content package with index definitions (e.g. below /oak:index)
* include the hook jar file in `META-INF/vault/hooks`
* install the content-package
 
The hook manages the properties `reindex` and `reindexCount` on an index
definition. So it makes sense to avoid using these two properties in the
content package.

Reindexing is triggered if an index definition has changed. I.e. if the
definition node or any of its descendants was added, modified or deleted.

# Maven Coordinates

    <dependency>
        <groupId>net.distilledcode</groupId>
        <artifactId>filevault-oak-reindex-hook</artifactId>
        <version><!-- latest version --></version>
    </dependency>

To find the latest version available, please look on [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22net.distilledcode%22%20AND%20a%3A%22filevault-oak-reindex-hook%22).
