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
package org.elephant.conda;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;

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

	private final String rootdir;

	final String condaCommand = SystemUtils.IS_OS_WINDOWS ? "condabin\\conda.bat" : "condabin/conda";

	final String pythonCommand = SystemUtils.IS_OS_WINDOWS ? "envs\\%s\\python.exe" : "envs/%s/bin/python";

	/**
	 * Returns a {@link ProcessBuilder} with the working directory specified in the
	 * constructor.
	 * 
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
			if ( SystemUtils.IS_OS_WINDOWS )
				new ProcessBuilder().command( "cmd.exe", "/c", "start", "/wait", "\"\"", tempFile.getAbsolutePath(), "/InstallationType=JustMe", "/AddToPath=0", "/RegisterPython=0", "/S", "/D=" + rootdir )
						.inheritIO().start().waitFor();
			else
				new ProcessBuilder().command( "bash", tempFile.getAbsolutePath(), "-b", "-p", rootdir )
						.inheritIO().start().waitFor();
		}
		this.rootdir = rootdir;

		// The following command will throw an exception if Conda does not work as
		// expected.
		final List< String > cmd = getBaseCommand();
		cmd.addAll( Arrays.asList( condaCommand, "-V" ) );
		getBuilder( false ).command( cmd ).start().waitFor();
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
		process.waitFor();
		return new BufferedReader( new InputStreamReader( process.getInputStream() ) ).readLine();
	}

	/**
	 * Run a Conda command with one or more arguments.
	 * 
	 * @params args One or more arguments for the Conda command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runConda( final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.add( condaCommand );
		cmd.addAll( Arrays.asList( args ) );
		getBuilder( true ).command( cmd ).start().waitFor();
	}

	/**
	 * Run a Python command in the specified environment.
	 * 
	 * @params envName The environment name used to run the Python command.
	 * @params args One or more arguments for the Python command.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public void runPython( final String envName, final String... args ) throws IOException, InterruptedException
	{
		final List< String > cmd = getBaseCommand();
		cmd.add( String.format( pythonCommand, envName ) );
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
		builder.command( cmd ).start().waitFor();
	}

	/**
	 * Returns a list of the Conda environments as {@code List< String >}.
	 * 
	 * @return The list of the Conda environments as {@code List< String >}.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public List< String > getEnvs() throws IOException
	{
		return Files.list( Paths.get( rootdir, "envs" ) )
				.map( p -> p.getFileName().toString() )
				.filter( p -> !p.startsWith( "." ) )
				.collect( Collectors.toList() );
	}

}
