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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.javaconda.CondaException.EnvironmentExistsException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Tests {@link Conda} class.
 * 
 * @author Ko Sugawara
 */
public class CondaTest
{

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	/**
	 * Tests that {@link Conda} runs properly after installation. The following
	 * methods are tested:
	 * <p>
	 * <ul>
	 * <li>{@link Conda#getVersion}</li>
	 * <li>{@link Conda#runConda}</li>
	 * <li>{@link Conda#runPythonIn}</li>
	 * <li>{@link Conda#getEnvironmentNames}</li>
	 * </ul>
	 * <p>
	 */
	@Test
	public void test()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			final String version = conda.getVersion();
			System.out.println( version );
			final String envName = "test";
			conda.runConda( "create", "-y", "-n", envName );
			conda.runConda( "install", "-y", "-n", envName, "python=3.8" );
			conda.runPythonIn( envName, "-m", "pip", "install", "cowsay" );
			final List< String > envNames = conda.getEnvironmentNames();
			assertThat( envNames, is( Arrays.asList( Conda.DEFAULT_ENVIRONMENT_NAME, envName ) ) );
			conda.runPythonIn( envName, "-c", "import cowsay; cowsay.cow('Hello World')" );
			final File pythonScript = new File( "src/test/resources/org/javaconda/output_json.py" );
			final Path jsonPath = Paths.get( folder.getRoot().getAbsolutePath(), "output.json" );
			conda.runPythonIn( envName, pythonScript.getAbsolutePath(), jsonPath.toString() );
			final Gson gson = new Gson();
			try (final Reader reader = Files.newBufferedReader( jsonPath ))
			{
				final User user = gson.fromJson( reader, User.class );
				final User expected = new User( 0, "test" );
				assertEquals( expected.id, user.id );
				assertEquals( expected.name, user.name );
			}
		}
		catch ( final IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

	/**
	 * Tests {@link Conda#uninstall} and {@link Conda#pipUninstall}.
	 */
	@Test
	public void testUninstall()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			final String version = conda.getVersion();
			System.out.println( version );
			final String envName = "test";
			conda.create( envName );
			conda.activate( envName );
			conda.install( "python=3.8" );
			conda.pipInstall( "cowsay" );
			conda.pipUninstall( "cowsay" );
			conda.uninstall( "python=3.8" );
		}
		catch ( final IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

	private class User
	{
		public final int id;

		public final String name;

		public User( final int id, final String name )
		{
			this.id = id;
			this.name = name;
		}

	}

	/**
	 * Tests that {@link Conda} can handle Conda environment-associated environment
	 * variables.
	 * 
	 */
	@Test
	public void testEnvironmentVariables()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			final String envName = "test_envvars";
			final File envFile = new File( "src/test/resources/org/javaconda/environment.yml" );
			conda.create( envName, "-f", envFile.getAbsolutePath() );
			final Map< String, String > inMap = conda.getEnvironmentVariables( envName );
			inMap.forEach( ( key, value ) -> System.out.println( key + ":" + value ) );
			assertEquals( "valueA", inMap.get( "JAVACONDA_TEST_VAR1" ) );
			assertEquals( "valueB", inMap.get( "JAVACONDA_TEST_VAR2" ) );
			final File pythonScript = new File( "src/test/resources/org/javaconda/output_envvars.py" );
			final Path jsonPath = Paths.get( folder.getRoot().getAbsolutePath(), "environment.json" );
			conda.runPythonIn( envName, pythonScript.getAbsolutePath(), jsonPath.toString() );
			final Gson gson = new Gson();
			try (final Reader reader = Files.newBufferedReader( jsonPath ))
			{
				final Type type = new TypeToken< Map< String, String > >()
				{}.getType();
				final Map< String, String > outMap = gson.fromJson( reader, type );
				assertEquals( "valueA", outMap.get( "JAVACONDA_TEST_VAR1" ) );
				assertEquals( "valueB", outMap.get( "JAVACONDA_TEST_VAR2" ) );
			}
		}
		catch ( final IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

	/**
	 * Test {@link Conda#create} if it throws an {@link EnvironmentExistsException}
	 * on duplicate environment creation.
	 */
	@Test( expected = EnvironmentExistsException.class )
	public void testDuplicateEnvironmentCreation()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			final String envName = "test";
			conda.create( envName );
			conda.create( envName );
		}
		catch ( IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

	/**
	 * Test the base environment.
	 */
	@Test
	public void testBaseEnvironment()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			conda.install( "python=3.8" );
			conda.pipInstall( "cowsay" );
			final List< String > envNames = conda.getEnvironmentNames();
			assertThat( envNames, is( Arrays.asList( Conda.DEFAULT_ENVIRONMENT_NAME ) ) );
			conda.runPython( "-c", "import cowsay; cowsay.cow('Hello World')" );
			final File pythonScript = new File( "src/test/resources/org/javaconda/output_json.py" );
			final Path jsonPath = Paths.get( folder.getRoot().getAbsolutePath(), "output.json" );
			conda.runPython( pythonScript.getAbsolutePath(), jsonPath.toString() );
			final Gson gson = new Gson();
			try (final Reader reader = Files.newBufferedReader( jsonPath ))
			{
				final User user = gson.fromJson( reader, User.class );
				final User expected = new User( 0, "test" );
				assertEquals( expected.id, user.id );
				assertEquals( expected.name, user.name );
			}
		}
		catch ( final IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

	/**
	 * Test {@link Conda#activate}.
	 */
	@Test
	public void testActivate()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			final String version = conda.getVersion();
			System.out.println( version );
			final String envName = "test";
			conda.create( envName );
			conda.activate( envName );
			assertEquals( envName, conda.getEnvName() );
			conda.install( "python=3.8" );
			conda.pipInstall( "cowsay" );
			final List< String > envNames = conda.getEnvironmentNames();
			assertThat( envNames, is( Arrays.asList( Conda.DEFAULT_ENVIRONMENT_NAME, envName ) ) );
			conda.runPython( "-c", "import cowsay; cowsay.cow('Hello World')" );
			final File pythonScript = new File( "src/test/resources/org/javaconda/output_json.py" );
			final Path jsonPath = Paths.get( folder.getRoot().getAbsolutePath(), "output.json" );
			conda.runPython( pythonScript.getAbsolutePath(), jsonPath.toString() );
			final Gson gson = new Gson();
			try (final Reader reader = Files.newBufferedReader( jsonPath ))
			{
				final User user = gson.fromJson( reader, User.class );
				final User expected = new User( 0, "test" );
				assertEquals( expected.id, user.id );
				assertEquals( expected.name, user.name );
			}
		}
		catch ( final IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

	/**
	 * Test {@link Conda#deactivate}.
	 */
	@Test( expected = RuntimeException.class )
	public void testDeactivate()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			final String version = conda.getVersion();
			System.out.println( version );
			final String envName = "test";
			conda.create( envName );
			conda.activate( envName );
			assertEquals( envName, conda.getEnvName() );
			conda.install( "python=3.8" );
			conda.pipInstall( "cowsay" );
			conda.runPython( "-c", "import cowsay; cowsay.cow('Hello World')" );
			conda.deactivate();
			assertEquals( Conda.DEFAULT_ENVIRONMENT_NAME, conda.getEnvName() );
			conda.runPython( "-c", "import cowsay; cowsay.cow('Hello World')" );
		}
		catch ( final IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

	/**
	 * Test {@link Conda#update}.
	 */
	@Test
	public void testUpdate()
	{
		try
		{
			final Conda conda = new Conda( Paths.get( folder.getRoot().getAbsolutePath(), "miniconda3" ).toString() );
			final ComparableVersion versionBefore = new ComparableVersion( conda.getVersion() );
			conda.update( "conda" );
			final ComparableVersion versionAfter = new ComparableVersion( conda.getVersion() );
			assertTrue( versionBefore.compareTo( versionAfter ) <= 0 );
		}
		catch ( final IOException | InterruptedException e )
		{
			fail( ExceptionUtils.getStackTrace( e ) );
		}
	}

}
