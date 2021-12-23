/*******************************************************************************
 * Copyright (C) 2021, Ko Sugawara
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
package org.javaconda;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.javaconda.CondaException.EnvironmentExistsException;

/**
 * Conda wrapper.
 * 
 * @author Ko Sugawara
 */
public class Conda
{

	private final static int TIMEOUT_MILLIS = 10 * 1000;

	private final static String DOWNLOAD_URL_LINUX = "https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh";

	private final static String DOWNLOAD_URL_MAC = "https://repo.anaconda.com/miniconda/Miniconda3-latest-MacOSX-x86_64.sh";

	private final static String DOWNLOAD_URL_MAC_M1 = "https://repo.anaconda.com/miniconda/Miniconda3-latest-MacOSX-arm64.sh";

	private final static String DOWNLOAD_URL_WIN = "https://repo.anaconda.com/miniconda/Miniconda3-latest-Windows-x86_64.exe";

	public final static String DEFAULT_ENVIRONMENT_NAME = "base";

	private final String rootdir;

	private String envName = DEFAULT_ENVIRONMENT_NAME;

	final String condaCommand = SystemUtils.IS_OS_WINDOWS ? "condabin\\conda.bat" : "condabin/conda";

	final String pythonCommand = SystemUtils.IS_OS_WINDOWS ? "python.exe" : "bin/python";

	/**
	 * Returns a {@link ProcessBuilder} with the working directory specified in the
	 * constructor.
	 * 
	 * @param isInheritIO
	 *            Sets the source and destination for subprocess standard I/O to be
	 *            the same as those of the current Java process.
	 * @return The {@link ProcessBuilder} with the working directory specified in
	 *         the constructor.
	 */
	private ProcessBuilder getBuilder( final boolean isInheritIO )
	{
		final ProcessBuilder builder = new ProcessBuilder().directory( new File( rootdir ) );
		if ( isInheritIO )
			builder.inheritIO();
		return builder;
	}

	/**
	 * Returns {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for
	 * Mac/Linux.
	 * 
	 * @return {@code \{"cmd.exe", "/c"\}} for Windows and an empty list for
	 *         Mac/Linux.
	 * @throws IOException
	 */
	private List< String > getBaseCommand()
	{
		final List< String > cmd = new ArrayList<>();
		if ( SystemUtils.IS_OS_WINDOWS )
			cmd.addAll( Arrays.asList( "cmd.exe", "/c" ) );
		return cmd;
	}

	/**
	 * Create a new Conda object. The root dir for Conda installation can be
	 * specified as {@code String}. If there is no directory found at the specified
	 * path, Miniconda will be automatically installed in the path. It is expected
	 * that the Conda installation has executable commands as shown below:
	 * 
	 * <pre>
	 * CONDA_ROOT
	 * ├── condabin
	 * │   ├── conda(.bat)
	 * │   ... 
	 * ├── envs
	 * │   ├── your_env
	 * │   │   ├── python(.exe)
	 * </pre>
	 * 
	 * @param rootdir
	 *            The root dir for Conda installation.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public Conda( final String rootdir ) throws IOException, InterruptedException
	{
		if ( Files.notExists( Paths.get( rootdir ) ) )
		{
			String downloadUrl = null;
			if ( SystemUtils.IS_OS_WINDOWS )
				downloadUrl = DOWNLOAD_URL_WIN;
			else if ( SystemUtils.IS_OS_LINUX )
				downloadUrl = DOWNLOAD_URL_LINUX;
			else if ( SystemUtils.IS_OS_MAC && System.getProperty( "os.arch" ).equals( "aarch64" ) )
				downloadUrl = DOWNLOAD_URL_MAC_M1;
			else if ( SystemUtils.IS_OS_MAC )
				downloadUrl = DOWNLOAD_URL_MAC;
			else
				throw new UnsupportedOperationException();

			final File tempFile = File.createTempFile( "miniconda", SystemUtils.IS_OS_WINDOWS ? ".exe" : ".sh" );
			tempFile.deleteOnExit();
			FileUtils.copyURLToFile(
					new URL( downloadUrl ),
					tempFile,
					TIMEOUT_MILLIS,
					TIMEOUT_MILLIS );
			final List< String > cmd = getBaseCommand();

			if ( SystemUtils.IS_OS_WINDOWS )
				cmd.addAll( Arrays.asList( "start", "/wait", "\"\"", tempFile.getAbsolutePath(), "/InstallationType=JustMe", "/AddToPath=0", "/RegisterPython=0", "/S", "/D=" + rootdir ) );
			else
				cmd.addAll( Arrays.asList( "bash", tempFile.getAbsolutePath(), "-b", "-p", rootdir ) );
			if ( new ProcessBuilder().inheritIO().command( cmd ).start().waitFor() != 0 )
				throw new RuntimeException();
		}
		this.rootdir = rootdir;

		// The following command will throw an exception if Conda does not work as
		// expected.
		final List< String > cmd = getBaseCommand();
		cmd.addAll( Arrays.asList( condaCommand, "-V" ) );
		if ( getBuilder( false ).command( cmd ).start().waitFor() != 0 )
			throw new RuntimeException();
	}

	/**
	 * Run {@code conda update} in the activated environment. A list of packages to
	 * be updated and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be updated and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void update( final String... args ) throws IOException, InterruptedException
	{
		updateIn( envName, args );
	}

	/**
	 * Run {@code conda update} in the specified environment. A list of packages to
	 * update and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the update command.
	 * @param args
	 *            The list of packages to be updated and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void updateIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "update", "-y", "-n", envName ) );
		cmd.addAll( Arrays.asList( args ) );
		runConda( cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run {@code conda create} to create an empty conda environment.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void create( final String envName ) throws IOException, InterruptedException
	{
		create( envName, false );
	}

	/**
	 * Run {@code conda create} to create an empty conda environment.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param isForceCreation
	 *            Force creation of the environment if {@code true}. If this value
	 *            is {@code false} and an environment with the specified name
	 *            already exists, throw an {@link EnvironmentExistsException}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void create( final String envName, final boolean isForceCreation ) throws IOException, InterruptedException
	{
		if ( !isForceCreation && getEnvironmentNames().contains( envName ) )
			throw new EnvironmentExistsException();
		runConda( "create", "-y", "-n", envName );
	}

	/**
	 * Run {@code conda create} to create a new conda environment with a list of
	 * specified packages.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param args
	 *            The list of packages to be installed on environment creation and
	 *            extra parameters as {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void create( final String envName, final String... args ) throws IOException, InterruptedException
	{
		create( envName, false, args );
	}

	/**
	 * Run {@code conda create} to create a new conda environment with a list of
	 * specified packages.
	 * 
	 * @param envName
	 *            The environment name to be created.
	 * @param isForceCreation
	 *            Force creation of the environment if {@code true}. If this value
	 *            is {@code false} and an environment with the specified name
	 *            already exists, throw an {@link EnvironmentExistsException}.
	 * @param args
	 *            The list of packages to be installed on environment creation and
	 *            extra parameters as {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void create( final String envName, final boolean isForceCreation, final String... args ) throws IOException, InterruptedException
	{
		if ( !isForceCreation && getEnvironmentNames().contains( envName ) )
			throw new EnvironmentExistsException();
		try
		{
			final List< String > cmd = new ArrayList<>( Arrays.asList( "env", "create", "--force", "-n", envName ) );
			cmd.addAll( Arrays.asList( args ) );
			runConda( cmd.stream().toArray( String[]::new ) );
		}
		catch ( final RuntimeException e )
		{
			final List< String > cmd = new ArrayList<>( Arrays.asList( "create", "-y", "-n", envName ) );
			cmd.addAll( Arrays.asList( args ) );
			runConda( cmd.stream().toArray( String[]::new ) );
		}
	}

	/**
	 * This method works as if the user runs {@code conda activate envName}. This
	 * method internally calls {@link Conda#setEnvName(String)}.
	 * 
	 * @param envName
	 *            The environment name to be activated.
	 * @throws IOException
	 *             If an I/O error occurs.
	 */
	public void activate( final String envName ) throws IOException
	{
		if ( getEnvironmentNames().contains( envName ) )
			setEnvName( envName );
		else
			throw new IllegalArgumentException( "environment: " + envName + " not found." );
	}

	/**
	 * This method works as if the user runs {@code conda deactivate}. This method
	 * internally sets the {@code envName} to {@code base}.
	 */
	public void deactivate()
	{
		setEnvName( DEFAULT_ENVIRONMENT_NAME );
	}

	/**
	 * This method is used by {@code Conda#activate(String)} and
	 * {@code Conda#deactivate()}. This method is kept private since it is not
	 * expected to call this method directory.
	 * 
	 * @param envName
	 *            The environment name to be set.
	 */
	private void setEnvName( final String envName )
	{
		this.envName = envName;
	}

	/**
	 * Returns the active environment name.
	 * 
	 * @return The active environment name.
	 * 
	 */
	public String getEnvName()
	{
		return envName;
	}

	/**
	 * Run {@code conda install} in the activated environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void install( final String... args ) throws IOException, InterruptedException
	{
		installIn( envName, args );
	}

	/**
	 * Run {@code conda install} in the specified environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the install command.
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void installIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "install", "-y", "-n", envName ) );
		cmd.addAll( Arrays.asList( args ) );
		runConda( cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run {@code conda uninstall} in the activated environment. A list of packages
	 * to uninstall and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be uninstalled and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void uninstall( final String... args ) throws IOException, InterruptedException
	{
		uninstallIn( envName, args );
	}

	/**
	 * Run {@code conda uninstall} in the specified environment. A list of packages
	 * to uninstall and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the uninstall command.
	 * @param args
	 *            The list of packages to be uninstalled and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void uninstallIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "uninstall", "-y", "-n", envName ) );
		cmd.addAll( Arrays.asList( args ) );
		runConda( cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run {@code pip install} in the activated environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void pipInstall( final String... args ) throws IOException, InterruptedException
	{
		pipInstallIn( envName, args );
	}

	/**
	 * Run {@code pip install} in the specified environment. A list of packages to
	 * install and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the install command.
	 * @param args
	 *            The list of packages to be installed and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void pipInstallIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "-m", "pip", "install" ) );
		cmd.addAll( Arrays.asList( args ) );
		runPythonIn( envName, cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run {@code pip uninstall} in the activated environment. A list of packages to
	 * uninstall and extra parameters can be specified as {@code args}.
	 * 
	 * @param args
	 *            The list of packages to be uninstalled and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void pipUninstall( final String... args ) throws IOException, InterruptedException
	{
		pipUninstallIn( envName, args );
	}

	/**
	 * Run {@code pip uninstall} in the specified environment. A list of packages to
	 * uninstall and extra parameters can be specified as {@code args}.
	 * 
	 * @param envName
	 *            The environment name to be used for the uninstall command.
	 * @param args
	 *            The list of packages to be uninstalled and extra parameters as
	 *            {@code String...}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void pipUninstallIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = new ArrayList<>( Arrays.asList( "-m", "pip", "uninstall", "-y" ) );
		cmd.addAll( Arrays.asList( args ) );
		runPythonIn( envName, cmd.stream().toArray( String[]::new ) );
	}

	/**
	 * Run a Python command in the activated environment. This method automatically
	 * sets environment variables associated with the activated environment. In
	 * Windows, this method also sets the {@code PATH} environment variable so that
	 * the specified environment runs as expected.
	 * 
	 * @param args
	 *            One or more arguments for the Python command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runPython( final String... args ) throws IOException, InterruptedException
	{
		runPythonIn( envName, args );
	}

	/**
	 * Run a Python command in the specified environment. This method automatically
	 * sets environment variables associated with the specified environment. In
	 * Windows, this method also sets the {@code PATH} environment variable so that
	 * the specified environment runs as expected.
	 * 
	 * @param envName
	 *            The environment name used to run the Python command.
	 * @param args
	 *            One or more arguments for the Python command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runPythonIn( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		if ( envName.equals( DEFAULT_ENVIRONMENT_NAME ) )
			cmd.add( pythonCommand );
		else
			cmd.add( Paths.get( "envs", envName, pythonCommand ).toString() );
		cmd.addAll( Arrays.asList( args ) );
		final ProcessBuilder builder = getBuilder( true );
		if ( SystemUtils.IS_OS_WINDOWS )
		{
			final Map< String, String > envs = builder.environment();
			final String envDir = Paths.get( rootdir, "envs", envName ).toString();
			envs.put( "Path", envDir + ";" + envs.get( "Path" ) );
			envs.put( "Path", Paths.get( envDir, "Scripts" ).toString() + ";" + envs.get( "Path" ) );
			envs.put( "Path", Paths.get( envDir, "Library" ).toString() + ";" + envs.get( "Path" ) );
			envs.put( "Path", Paths.get( envDir, "Library", "Bin" ).toString() + ";" + envs.get( "Path" ) );
		}
		builder.environment().putAll( getEnvironmentVariables( envName ) );
		if ( builder.command( cmd ).start().waitFor() != 0 )
			throw new RuntimeException();
	}

	/**
	 * Returns Conda version as a {@code String}.
	 * 
	 * @return The Conda version as a {@code String}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public String getVersion() throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.addAll( Arrays.asList( condaCommand, "-V" ) );
		final Process process = getBuilder( false ).command( cmd ).start();
		if ( process.waitFor() != 0 )
			throw new RuntimeException();
		return new BufferedReader( new InputStreamReader( process.getInputStream() ) ).readLine();
	}

	/**
	 * Run a Conda command with one or more arguments.
	 * 
	 * @param args
	 *            One or more arguments for the Conda command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runConda( final String... args ) throws RuntimeException, IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.add( condaCommand );
		cmd.addAll( Arrays.asList( args ) );
		if ( getBuilder( true ).command( cmd ).start().waitFor() != 0 )
			throw new RuntimeException();
	}

	/**
	 * Returns environment variables associated with the activated environment as
	 * {@code Map< String, String >}.
	 * 
	 * @return The environment variables as {@code Map< String, String >}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public Map< String, String > getEnvironmentVariables() throws IOException, InterruptedException
	{
		return getEnvironmentVariables( envName );
	}

	/**
	 * Returns environment variables associated with the specified environment as
	 * {@code Map< String, String >}.
	 * 
	 * @param envName
	 *            The environment name used to run the Python command.
	 * @return The environment variables as {@code Map< String, String >}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public Map< String, String > getEnvironmentVariables( final String envName ) throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.addAll( Arrays.asList( condaCommand, "env", "config", "vars", "list", "-n", envName ) );
		final Process process = getBuilder( false ).command( cmd ).start();
		if ( process.waitFor() != 0 )
			throw new RuntimeException();
		final Map< String, String > map = new HashMap<>();
		try (final BufferedReader reader = new BufferedReader( new InputStreamReader( process.getInputStream() ) ))
		{
			String line;

			while ( ( line = reader.readLine() ) != null )
			{
				final String[] keyVal = line.split( " = " );
				map.put( keyVal[ 0 ], keyVal[ 1 ] );
			}
		}
		return map;
	}

	/**
	 * Returns a list of the Conda environment names as {@code List< String >}.
	 * 
	 * @return The list of the Conda environment names as {@code List< String >}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public List< String > getEnvironmentNames() throws IOException
	{
		final List< String > envs = new ArrayList<>( Arrays.asList( DEFAULT_ENVIRONMENT_NAME ) );
		envs.addAll( Files.list( Paths.get( rootdir, "envs" ) )
				.map( p -> p.getFileName().toString() )
				.filter( p -> !p.startsWith( "." ) )
				.collect( Collectors.toList() ) );
		return envs;
	}

}
