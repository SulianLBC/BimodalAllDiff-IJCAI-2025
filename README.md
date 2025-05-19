This is the code for the article "Bimodal Depth-First Search for Scalable GAC for AllDifferent" accepted at IJCAI 2025.

The bimodal approach for the AllDifferent constraint is implemented in the AlgoAllDiffBimodal java class.

```java
import org.chocosolver.solver.constraints.nary.alldifferent.algo.AlgoAllDiffBimodal;
```

Among the data structures used in the bimodal algorithm, two were added to Choco: the matching and the tracking list.
They are implemented in the BipartiteMatching and TrackingList java classes respectively.

```java
import org.chocosolver.util.objects.BipartiteMatching;
import org.chocosolver.util.objects.TrackingList;
```


<a name="dow"></a>
## How to use this project ? ##

General requirements:
* JDK 9+
* maven 3+

### Examples module ###

Some examples of problems and Choco models are available in the examples' module.

```java
import org.chocosolver.examples;
```

When posting an AllDifferent constraint, the user can add a second argument, additionally to the set of variables, to specify which filtering algorithm will be used by the solver.
Here is a list of possible options:

```java
String[] possibleOptions = {"BC", "AC_REGIN", "AC_ZHANG", "AC_CLASSIC", "AC_COMPLEMENT", "AC_PARTIAL", "AC_TUNED"};
   ```

Here is an example:

```java
public void buildModel() {
model = new Model("NQueen");
vars = new IntVar[n];
IntVar[] diag1 = new IntVar[n];
IntVar[] diag2 = new IntVar[n];

        for (int i = 0; i < n; i++) {
            vars[i] = model.intVar("Q_" + i, 1, n, false);
            diag1[i] = model.offset(vars[i], i);
            diag2[i] = model.offset(vars[i], -i);
        }
        String consistency = "AC_TUNED";
        model.allDifferent(vars, consistency).post();
        model.allDifferent(diag1, consistency).post();
        model.allDifferent(diag2, consistency).post();
    }
 ```

### Command line with a MiniZinc model ###

Additional requirements:
* MiniZinc 2.8+
* git

1. Create the jar file of the project --> open a terminal at the root of the project and run the following command:
```
mvn install -DskipTests
```

2. Convert a MiniZinc file (eg. ```queens.mzn``` ) with a data file (eg. ```1000.dzn```):

```
minizinc -I ./parsers/src/main/minizinc/mzn_lib -c -m
/Users/kyzrsoze/Sources/MiniZinc/modelsForAlldifferent/nqueens/queens4.mzn -d
/Users/kyzrsoze/Sources/MiniZinc/modelsForAlldifferent/nqueens/1000.dzn -o nqueens-1000.fzn
```

3. Run Choco with the jar on the converted MiniZinc file:
```
java -jar ./parsers/target/choco-solver-5.0.0-beta.2-light.jar nqueens-1000.fzn
```

4. (Optionnal) Specify the filtering algorithm for the AllDifferent constraints thanks to the ```- ad``` option:



## old ##

Choco-solver is available on [Maven Central Repository](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22org.choco-solver%22%20AND%20a%3A%22choco-solver%22),
or directly from the [latest release](https://github.com/chocoteam/choco-solver/releases/latest).

[Snapshot releases](https://oss.sonatype.org/content/repositories/snapshots/org/choco-solver/choco-solver/) are also available for curious.

In the following, we distinguish two usages of Choco:

- as a standalone library: the jar file includes all required dependencies,
- as a library: the jar file excludes all dependencies.

The name of the jar file terms the packaging:
- `choco-solver-4.XX.Y-jar-with-dependencies.jar` or 
- `choco-solver-4.XX.Y.jar`.
- `choco-parsers-4.XX.Y-jar-with-dependencies.jar` or
- `choco-parsers-4.XX.Y-light.jar` or
- `choco-parsers-4.XX.Y.jar`.

The `light` tagged jar file is a version of the `jar-with-dependencies` one with dependencies from this archive.

A [Changelog file](./CHANGES.md) is maintained for each release.



### As a stand-alone library ###

The jar file contains all required dependencies.
The next step is simply to add the jar file to your classpath of your application.
Note that if your program depends on dependencies declared in the jar file,
you should consider using choco as a library.

### As a library ###

The jar file does not contain any dependencies,
as of being used as a dependency of another application.
The next step is to add the jar file to your classpath of your application and also add the required dependencies.


### Building from sources ###

The source of the released versions are directly available in the `Tag` section.
You can also download them using github features.
Once downloaded, move to the source directory then execute the following command
to make the jar:

    $ mvn clean package -DskipTests

If the build succeeded, the resulting jar will be automatically
installed in your local maven repository and available in the `target` sub-folders.



_Sulian Le Bozec-Chiffoleau_
