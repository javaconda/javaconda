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

	private final String path;

	final String condaCommand = SystemUtils.IS_OS_WINDOWS ? "condabin/conda.exe" : "condabin/conda";

	final String pythonCommand = SystemUtils.IS_OS_WINDOWS ? "envs/%s/python.exe" : "envs/%s/bin/python";

	/**
	 * Returns a {@link ProcessBuilder} with the working directory specified in the
	 * constructor.
	 * 
	 * @return The {@link ProcessBuilder} with the working directory specified in
	 *         the constructor.
	 */
	private ProcessBuilder getBuilder()
	{
		return new ProcessBuilder().directory( new File( path ) );
	}

	/**
	 * Create a new Conda object. The root path for Conda installation can be
	 * specified as {@code String}. If there is no directory found at the specified
	 * path, Miniconda will be automatically installed in the path. It is expected
	 * that the Conda installation has executable commands as shown below:
	 * 
	 * <pre>
	 * CONDA_ROOT
	 * ├── condabin
	 * │   ├── conda(.exe)
	 * │   ... ├── envs
	 * │   ├── your_env
	 * │   │   ├── python(.exe)
	 * </pre>
	 * 
	 * @param path
	 *            The root path for Conda installation.
	 * @throws IOException
	 *             If an I/O error occurs.
	 * @throws InterruptedException
	 *             If the current thread is interrupted by another thread while it
	 *             is waiting, then the wait is ended and an InterruptedException is
	 *             thrown.
	 */
	public Conda( final String path ) throws IOException, InterruptedException
	{
		if ( Files.notExists( Paths.get( path ) ) )
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
				new ProcessBuilder().command( "start", "/wait", "\"\"", tempFile.getAbsolutePath(), "/InstallationType=JustMe", "/RegisterPython=0", "/S", path )
						.inheritIO().start().waitFor();
			else
				new ProcessBuilder().command( "bash", tempFile.getAbsolutePath(), "-b", "-p", path )
						.inheritIO().start().waitFor();
		}
		this.path = path;

		// The following command will throw an exception if Conda does not work as
		// expected.
		getBuilder().command( condaCommand, "-V" ).start().waitFor();
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
		final Process process = getBuilder().command( condaCommand, "-V" ).start();
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
		final List< String > cmd = new ArrayList<>();
		cmd.add( condaCommand );
		cmd.addAll( Arrays.asList( args ) );
		getBuilder().command( cmd ).inheritIO().start().waitFor();
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
		final List< String > cmd = new ArrayList<>();
		cmd.add( String.format( pythonCommand, envName ) );
		cmd.addAll( Arrays.asList( args ) );
		getBuilder().command( cmd ).inheritIO().start().waitFor();
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
		return Files.list( Paths.get( path, "envs" ) )
				.map( p -> p.getFileName().toString() )
				.filter( p -> !p.startsWith( "." ) )
				.collect( Collectors.toList() );
	}

}
