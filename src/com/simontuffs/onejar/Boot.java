/*
 * Copyright (c) 2004, P. Simon Tuffs (simon@simontuffs.com)
 * All rights reserved.
 *
 * See full license at http://one-jar.sourceforge.net/one-jar-license.txt
 * This license is also included in the distributions of this software
 * under doc/one-jar-license.txt
 */	 

package com.simontuffs.onejar;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Run a java application which requires multiple support jars from inside
 * a single jar file.
 * 
 * <p>
 * Developer time JVM properties:
 * <pre>
 *   -Done-jar.main-class={name}  Use named class as main class to run. 
 *   -Done-jar.record[=recording] Record loaded classes into "recording" directory.
 *                                Flatten jar-names into directory tree suitable 
 * 								  for use as a classpath.
 *   -Done-jar.jar-names          Record loaded classes, preserve jar structure
 *   -Done-jar.verbose            Run the JarClassLoader in verbose mode.
 * </pre>
 * @author simon@simontuffs.com (<a href="http://www.simontuffs.com">http://www.simontuffs.com</a>)
 */
public class Boot {
	
	/**
	 * The name of the manifest attribute which controls which class 
	 * to bootstrap from the jar file.  The boot class can
	 * be in any of the contained jar files.
	 */
	public final static String BOOT_CLASS = "Boot-Class";
	
	public final static String MANIFEST = "META-INF/MANIFEST.MF";
	public final static String MAIN_JAR = "main/main.jar";

	public final static String WRAP_CLASS_LOADER = "Wrap-Class-Loader";
	public final static String WRAP_JAR = "/wrap/wraploader.jar";

	public final static String PROPERTY_PREFIX = "one-jar.";
	public final static String MAIN_CLASS = PROPERTY_PREFIX + "main-class";
	public final static String RECORD = PROPERTY_PREFIX + "record";
	public final static String JARNAMES = PROPERTY_PREFIX + "jar-names";
	public final static String VERBOSE = PROPERTY_PREFIX + "verbose";
	public final static String INFO = PROPERTY_PREFIX + "info";
	
	protected static boolean info, verbose;

	// Singleton loader.
	protected static JarClassLoader loader = null;
	
	public static JarClassLoader getClassLoader() {
		return loader;
	}

	protected static void VERBOSE(String message) {
		if (verbose) System.out.println("Boot: " + message);
	}

	protected static void WARNING(String message) {
		System.err.println("Boot: Warning: " + message); 
	}
	
	protected static void INFO(String message) {
		if (info) System.out.println("Boot: Info: " + message);
	}

    public static void main(String[] args) throws Exception {
    	run(args);
    }
    
    public static void run(String args[]) throws Exception {
    	
		if (false) {
			// What are the system properties.
	    	Properties props = System.getProperties();
	    	Enumeration enum = props.keys();
	    	
	    	while (enum.hasMoreElements()) {
	    		String key = (String)enum.nextElement();
	    		System.out.println(key + "=" + props.get(key));
	    	}
		}
		
	    	
    	String prefix = "Boot: ";
    	// Is the main class specified on the command line?  If so, boot it.
    	// Othewise, read the main class out of the manifest.
		String mainClass = null, recording = null;
		boolean record = false, jarnames = false;
		boolean verbose = false;

		{
			// Default properties are in resource 'one-jar.properties'.
			Properties properties = new Properties();
			String props = "/one-jar.properties";
			InputStream is = Boot.class.getResourceAsStream(props); 
			if (is != null) {
				INFO("loading properties from " + props);
				properties.load(is);
			}
				 
			// Merge in anything in a local file with the same name.
			props = "file:one-jar.properties";
			is = Boot.class.getResourceAsStream(props);
			if (is != null) {
				INFO("loading properties from " + props);
				properties.load(is);
			} 
			// Set system properties only if not already specified.
			Enumeration enum = properties.propertyNames();
			while (enum.hasMoreElements()) {
				String name = (String)enum.nextElement();
				if (System.getProperty(name) == null) {
					System.setProperty(name, properties.getProperty(name));
				}
			}
		}		
		// Process developer properties:
		mainClass = System.getProperty(MAIN_CLASS);
		if (System.getProperties().containsKey(RECORD)) {
			record = true;
			recording = System.getProperty(RECORD);
			if (recording.length() == 0) recording = null;
    	} 
		if (System.getProperties().containsKey(JARNAMES)) {
			record = true;
			jarnames = true;
		}
		
		if (System.getProperties().containsKey(VERBOSE)) {
			verbose = true;
		} 
		if (System.getProperties().containsKey(INFO)) {
			info = true;
		} 

		// If no main-class specified, check the manifest of the main jar for
		// a Boot-Class attribute.
		if (mainClass == null) {
	    	// Hack to obtain the name of this jar file.
	    	String jar = System.getProperty(JarClassLoader.JAVA_CLASS_PATH);
	    	JarFile jarFile = new JarFile(jar);
	    	Manifest manifest = jarFile.getManifest();
			Attributes attributes = manifest.getMainAttributes();
			mainClass = attributes.getValue(BOOT_CLASS);
		}
		
		if (mainClass == null) {
			// Still don't have one (default).  One final try: look for a jar file in a
			// main directory.  There should be only one, and it's manifest 
			// Main-Class attribute is the main class.  The JarClassLoader will take
			// care of finding it.
			InputStream is = Boot.class.getResourceAsStream("/" + MAIN_JAR);
			if (is != null) {
				JarInputStream jis = new JarInputStream(is);
				Manifest manifest = jis.getManifest();
				Attributes attributes = manifest.getMainAttributes();
				mainClass = attributes.getValue(Attributes.Name.MAIN_CLASS);
			}
		}
	
		// Do we need to create a wrapping classloader?  Check for the
		// presence of a "wrap" directory at the top of the jar file.
		URL url = Boot.class.getResource(WRAP_JAR);
		
		if (url != null) {
			// Wrap class loaders.
			JarClassLoader bootLoader = new JarClassLoader("wrap");
			bootLoader.setRecord(record);
			bootLoader.setFlatten(!jarnames);
			bootLoader.setRecording(recording);
			// Note: order of setInfo & setVerbose is significant, since verbose => info
			// but not vice-versa.
			bootLoader.setInfo(info);
			bootLoader.setVerbose(verbose);
			bootLoader.load(null);
			
			// Read the "Wrap-Class-Loader" property from the wraploader jar file.
			// This is the class to use as a wrapping class-loader.
			JarInputStream jis = new JarInputStream(Boot.class.getResourceAsStream(WRAP_JAR));
			String wrapLoader = jis.getManifest().getMainAttributes().getValue(WRAP_CLASS_LOADER);
			if (wrapLoader == null) {
				WARNING(url + " did not contain a " + WRAP_CLASS_LOADER + " attribute, unable to load wrapping classloader");
			} else {
				INFO("using " + wrapLoader);
				Class jarLoaderClass = bootLoader.loadClass(wrapLoader);
				Constructor ctor = jarLoaderClass.getConstructor(new Class[]{ClassLoader.class});
				loader = (JarClassLoader)ctor.newInstance(new Object[]{bootLoader});
			}
				
		} else {
			INFO("using JarClassLoader");
			loader = new JarClassLoader(Boot.class.getClassLoader());
		}
		loader.setRecord(record);
		loader.setFlatten(!jarnames);
		loader.setRecording(recording);
		loader.setInfo(info);
		loader.setVerbose(verbose);
		mainClass = loader.load(mainClass);

		// Set the context classloader in case any classloaders delegate to it.
		// Otherwise it would default to the sun.misc.Launcher$AppClassLoader which
		// is used to launch the jar application, and attempts to load through
		// it would fail if that code is encapsulated inside the one-jar.
		Thread.currentThread().setContextClassLoader(loader);
		
    	Class cls = loader.loadClass(mainClass);
    	Method main = cls.getMethod("main", new Class[]{String[].class}); 
    	main.invoke(null, new Object[]{args});
    }
}
