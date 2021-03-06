package com.github.zachdeibert.mavendependencyplugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;

/**
 * The main mojo that injects the runtime code into a jar so it can download
 * everything automatically
 * 
 * @author Zach Deibert
 * @since 1.0.0
 */
@Mojo(name = "inject-runtime", defaultPhase = LifecyclePhase.PACKAGE)
public class RuntimeInjectionMojo extends AbstractMojo {
	/**
	 * The Maven project
	 * 
	 * @since 1.0.0
	 */
	@Parameter(defaultValue = "${project}", required = true)
	private MavenProject project;
	/**
	 * The artifact resolver
	 * 
	 * @since 1.0.0
	 */
	@Component
	private ArtifactResolver artifactResolver;
	/**
	 * The Maven session
	 * 
	 * @since 1.0.0
	 */
	@Parameter(defaultValue = "${session}", required = true)
	private MavenSession session;
	/**
	 * The class that manages the artifact handlers
	 * 
	 * @since 1.0.0
	 */
	@Component
	private ArtifactHandlerManager artifactHandlerManager;
	/**
	 * The version of the runtime library to use
	 * 
	 * @since 1.0.0
	 */
	@Parameter(defaultValue = Artifact.RELEASE_VERSION)
	private String runtimeVersion;
	/**
	 * The class that should be called after the dependencies are set up
	 * 
	 * @since 1.0.0
	 */
	@Parameter
	private String mainClass;

	/**
	 * Runs the mojo
	 * 
	 * @since 1.0.0
	 */
	public void execute() throws MojoExecutionException, MojoFailureException {
		File target = project.getArtifact().getFile();
		if (!target.exists()) {
			throw new MojoFailureException("Target jar has not been packaged yet");
		}
		File runtime;
		try {
			ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(
					session.getProjectBuildingRequest());
			buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
			Artifact artifact = new DefaultArtifact("com.github.zachdeibert", "maven-dependency-runtime",
					runtimeVersion, Artifact.SCOPE_RUNTIME, "jar", null,
					artifactHandlerManager.getArtifactHandler("jar"));
			ArtifactResult res = artifactResolver.resolveArtifact(buildingRequest, artifact);
			runtime = res.getArtifact().getFile();
		} catch (ArtifactResolverException ex) {
			getLog().error(ex);
			throw new MojoFailureException("Unable to resolve dependency");
		}
		File temp;
		try {
			temp = File.createTempFile("dependency-runtime-maven-plugin-", ".jar");
		} catch (IOException ex) {
			getLog().error(ex);
			throw new MojoFailureException("Unable to create temporary file");
		}
		FileInputStream istream = null;
		JarInputStream ijar = null;
		FileOutputStream ostream = null;
		JarOutputStream ojar = null;
		try {
			Set<String> files = new HashSet<String>();
			istream = new FileInputStream(target);
			ijar = new JarInputStream(istream);
			Manifest manifest = ijar.getManifest();
			if (manifest == null) {
				ijar.close();
				throw new MojoFailureException("No manifest found in jar");
			}
			Attributes attr = manifest.getMainAttributes();
			if (mainClass == null) {
				if (!attr.containsKey(Attributes.Name.MAIN_CLASS)) {
					throw new MojoFailureException("Main class not specified");
				}
				attr.putValue("Real-Main-Class", attr.get(Attributes.Name.MAIN_CLASS).toString());
			} else {
				attr.putValue("Real-Main-Class", mainClass);
			}
			attr.put(Attributes.Name.MAIN_CLASS, "com.github.zachdeibert.mavendependencyruntime.Main");
			ostream = new FileOutputStream(temp);
			ojar = new JarOutputStream(ostream, manifest);
			copyFiles(ijar, ojar, files);
			ijar.close();
			ijar = null;
			istream.close();
			istream = new FileInputStream(runtime);
			ijar = new JarInputStream(istream);
			copyFiles(ijar, ojar, files);
			ijar.close();
			ijar = null;
			istream.close();
			istream = null;
			ojar.close();
			ojar = null;
			ostream.close();
			ostream = null;
			target.delete();
			if (!temp.renameTo(target)) {
				istream = new FileInputStream(temp);
				ostream = new FileOutputStream(target);
				copyStream(istream, ostream);
				ostream.close();
				ostream = null;
				istream.close();
				istream = null;
			}
		} catch (IOException ex) {
			getLog().error(ex);
			throw new MojoFailureException("Unable to inject runtime code");
		} finally {
			if (ojar != null) {
				try {
					ojar.close();
				} catch (IOException ex) {
					getLog().error(ex);
				}
			}
			if (ostream != null) {
				try {
					ostream.close();
				} catch (IOException ex) {
					getLog().error(ex);
				}
			}
			if (ijar != null) {
				try {
					ijar.close();
				} catch (IOException ex) {
					getLog().error(ex);
				}
			}
			if (istream != null) {
				try {
					istream.close();
				} catch (IOException ex) {
					getLog().error(ex);
				}
			}
			if (temp.exists()) {
				temp.delete();
			}
		}
	}

	/**
	 * Copies all remaining data from the <code>in</code> stream into the
	 * <code>out</code> stream.
	 * 
	 * @param in
	 *            The stream to copy from
	 * @param out
	 *            The stream to copy to
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	private void copyStream(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[4096];
		for (int read = in.read(buffer); read > 0; read = in.read(buffer)) {
			out.write(buffer, 0, read);
		}
	}

	/**
	 * Copies all files from one jar to another unless they already exist in the
	 * output jar
	 * 
	 * @param in
	 *            The jar to copy files from
	 * @param out
	 *            The jar to copy files to
	 * @param files
	 *            The set of all files that already exist in the output jar
	 * @since 1.0.0
	 * @throws IOException
	 *             If an I/O error has occurred
	 */
	private void copyFiles(JarInputStream in, JarOutputStream out, Set<String> files) throws IOException {
		JarEntry entry;
		while ((entry = in.getNextJarEntry()) != null) {
			String name = entry.getName();
			if (!files.contains(name)) {
				files.add(name);
				out.putNextEntry(entry);
				copyStream(in, out);
			}
		}
	}
}
