# JavaConda

JavaConda is a Java wrapper for [Conda](https://docs.conda.io/en/latest/index.html).


## Getting started

### Create an instance of Conda class

```
final Conda conda = new Conda( Paths.get(System.getProperty("user.home"), "miniconda").toString() );
```

The root path for Conda installation can be specified as `String`.
If there is no directory found at the specified path, [Miniconda](https://docs.conda.io/en/latest/miniconda.html) will be automatically installed in the path.
It is expected that the Conda installation has executable commands as shown below:

```
CONDA_ROOT
├── condabin
│   ├── conda(.exe)
│   ...
├── envs
│   ├── your_env
│   │   ├── python(.exe)
``` 


### Get Conda version

```java
final String version = conda.getVersion();
System.out.println( version );
```

<details>
<summary>output</summary>

```
conda 4.10.3
```

</details>

### Create a Conda env

#### Create an empty environment

```java
final String envName = "test";
conda.runConda( "create", "-y", "-n", envName );
```

#### Create an environment from an environment file

```java
final File envFile = new File( "path/to/environment.yml" );
conda.runConda( "env", "create", "-f", envFile.getAbsolutePath() );
```

<details>
<summary><code>environment.yml</code> for installing Python 3.7 with two environment variables</summary>
<p>

```yaml
channels:
  - defaults
dependencies:
  - python=3.7
variables:
  JAVACONDA_TEST_VAR1: valueA
  JAVACONDA_TEST_VAR2: valueB
```

</details>

### Install Conda modules

```java
conda.runConda( "install", "-y", "-n", envName, "python=3.8" );
```

### Install Pip moduels

```java
conda.runPython( envName, "-m", "pip", "install", "cowsay" );
```

### List Conda environment names

```java
final List< String > envs = conda.getEnvs();
```

### Run a Conda command

```java
conda.runConda( "-V" );
conda.runConda( "-h" );
```

### Run a Python command in the specified environment

 This method automatically sets environment variables associated with the specified environment.\
 In Windows, this method also sets the `PATH` environment variable used by the specified environment.

```java
conda.runPython( envName, "-c", "import cowsay; cowsay.cow('Hello World')" );
```

<details>
<summary>output</summary>

```
  ___________
| Hello World |
  ===========
           \
            \
              ^__^
              (oo)\_______
              (__)\       )\/\
                  ||----w |
                  ||     ||
```

</details>

### Run a Python script in the specified environment

The following demo script (`output_json.py`) writes a dictionary to JSON.

```python
#! /usr/bin/env python
import argparse
import json

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('output', help='json file path for output')
    args = parser.parse_args()
    dictionary = {
        'id': 0,
        'name': 'test',
    }
    with open(args.output, 'w') as outfile:
        json.dump(dictionary, outfile)
```

The Python script can be run and the output (`output.json`) can be converted into a Java object using [Gson](https://github.com/google/gson) library.

<details>
<summary><code>class User</code></summary>

```java
class User
{
    public final int id;
    public final String name;

    public User( final int id, final String name )
    {
        this.id = id;
        this.name = name;
    }
}
```

</details>

```java
final File pythonScript = new File( "output_json.py" );
final Path jsonPath = Paths.get( "output.json" );
conda.runPython( envName, pythonScript.getAbsolutePath(), jsonPath.toString() );
final Gson gson = new Gson();
try (final Reader reader = Files.newBufferedReader( jsonPath ))
{
    final User user = gson.fromJson( reader, User.class );
    System.out.println( String.format( "User id: %d, User name: %s", user.id, user.name ) );
}
```
<details>
<summary>output</summary>

```
User id: 0, User name: test
```

</details>

### Get environment variables associated with the specified environment

```java
final Map< String, String > envvars = conda.getEnvironmentVariables( envName );
envvars.forEach( ( key, value ) -> System.out.println( key + ":" + value ) );
```

<details>
<summary>output</summary>

```
JAVACONDA_TEST_VAR1:valueA
JAVACONDA_TEST_VAR2:valueB
```

</details>