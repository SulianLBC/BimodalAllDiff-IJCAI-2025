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
* Git

1. Create the jar file of the project --> open a terminal at the root of the project and run the following command:
```
mvn install -DskipTests
```

2. Convert a MiniZinc file (e.g. ```model.mzn```) with a data file (e.g. ```data.dzn```) into a FlatZinc file (e.g. ```model_data.fzn```):
```
minizinc -I ./parsers/src/main/minizinc/mzn_lib -c -m /AbsolutePathToModelDirectory/model.mzn -d /AbsolutePathToDataDirectory/data.dzn -o model_data.fzn
```

3. Run Choco with the jar on the FlatZinc file:
```
java -jar ./parsers/target/choco-parsers-4.10.17-light.jar model_data.fzn
```

4. (Optionnal) Specify the filtering algorithm for the AllDifferent constraints by the ```-ad``` option:
```
java -jar ./parsers/target/choco-parsers-4.10.17-light.jar model_data.fzn -ad AC_TUNED
```


_Sulian Le Bozec-Chiffoleau_
