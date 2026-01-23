To run mutation testing on a single class using only its corresponding test class, use the following command:

```sh
./mvnw org.pitest:pitest-maven:mutationCoverage \
    -DtargetClasses=io.github.lorenzobettini.emfmodelgenerator.EMFInstanceCreatorFeatureSetter \
     -DtargetTests=io.github.lorenzobettini.emfmodelgenerator.EMFInstanceCreatorFeatureSetterTest
```

To run mutation testing with a custom timeout:

```sh
./mvnw org.pitest:pitest-maven:mutationCoverage -Dpit-timeout-const=1000
```