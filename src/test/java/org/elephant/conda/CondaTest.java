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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.gson.Gson;

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
	 * <li>{@link Conda#getVersion}
	 * <li>{@link Conda#runConda}
	 * <li>{@link Conda#runPython}
	 * <li>{@link Conda#getEnvs}
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
			conda.runPython( envName, "-m", "pip", "install", "cowsay" );
			final List< String > envs = conda.getEnvs();
			assertThat( envs, is( Arrays.asList( envName ) ) );
			conda.runPython( envName, "-c", "import cowsay; cowsay.cow('Hello World')" );
			final File pythonScript = new File( "src/test/resources/org/elephant/conda/output_json.py" );
			final Path jsonPath = Paths.get( folder.getRoot().getAbsolutePath(), "output.json" );
			conda.runPython( envName, pythonScript.getAbsolutePath(), jsonPath.toString() );
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

}
