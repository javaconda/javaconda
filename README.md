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

```
final String version = conda.getVersion();
System.out.println( version );
```

### Create a Conda env

```
final String envName = "test";
conda.runConda( "create", "-y", "-n", envName );
```

### Install Conda modules

```
conda.runConda( "install", "-y", "-n", envName, "python=3.8" );
```

### Install Pip moduels

```
conda.runPython( envName, "-m", "pip", "install", "cowsay" );
```

### List environment names

```
final List< String > envs = conda.getEnvs();
```

### Run a Conda command

```
conda.runConda( "-V" );
conda.runConda( "-h" );
```

### Run a Python command in the specified environment

```
conda.runPython( envName, "-c", "import cowsay; cowsay.cow('Hello World')" );
```

will output

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

### Run a Python script in the specified environment

As an example, the following script (`output_json.py`) writes a dictionary to JSON.

```
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

```
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

```
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
