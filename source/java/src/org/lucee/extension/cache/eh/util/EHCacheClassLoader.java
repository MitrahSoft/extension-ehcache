package org.lucee.extension.cache.eh.util;

import java.lang.reflect.Method;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;

import lucee.commons.io.log.Log;


/**
 * A special class loader that first attempts to load class from the Lucee Environment,
 * but that can fail when RMI classes are requested via a spawned thread, so when
 * the class cannot be located in the Lucee Environment, it searches all the Felix
 * bundles for the requested class. This will prevent issues with issues trying
 * to load classes like net.sf.ehcache.distribution.RMICachePeer_Stub. 
 *
 * @author Dan G. Switzer, II
 * @version 1.0
 */
public class EHCacheClassLoader extends ClassLoader {
	private Config config;
	private ClassLoader LuceeEnvClassLoader;

	public EHCacheClassLoader(Config config) {
		this.config = config;

		try {
			Method m = config.getClass().getMethod("getClassLoaderEnv", new Class[0]);
			this.LuceeEnvClassLoader = (ClassLoader) m.invoke(config, new Object[0]);
		}
		catch (Exception e) {
			// do nothing
		}
	}

	public CFMLEngine getCFMLEngine(){
		try {
			Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil().loadClass("lucee.runtime.config.ConfigWebUtil");
			Method m = clazz.getMethod("getEngine", new Class[] { Config.class });
			return (CFMLEngine) m.invoke(null, this.config);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public boolean isFrameworkBundle(Bundle b){
		try {
			Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil().loadClass("lucee.runtime.osgi.OSGiUtil");
			Method m = clazz.getMethod("isFrameworkBundle", new Class[] { Bundle.class });
			return (boolean) m.invoke(null, b);
		} catch (Exception e) {
			return false;
		}
	}

	private Log getLogger() {
		Log logger = this.config.getLog("application");

		return logger;
	}

	public Class<?> loadClass(final String className) throws ClassNotFoundException {
		Class<?> clazz = null;

		Log log = getLogger();

		log.debug("ehcache", "Loading class " + className);

		// we should try the finding the class in the Lucee environmental class loader
		if( this.LuceeEnvClassLoader != null ){
			try {
				clazz = (Class<?>)this.LuceeEnvClassLoader.loadClass(className);
			} catch (ClassNotFoundException ex) {
				// ignore that we cannot find the class
				log.debug("ehcache", "Could not find class in EnvClassLoader.");
			}
		}
		
		log.debug("ehcache", "Could not find in normal class loader, searching Felix bundles...");
		clazz = searchFelixBundleClasses(className);

		if( clazz == null ){
			log.error("ehcache", "Could not find class in any class loader!");
			throw new ClassNotFoundException();
		}

		return clazz;
	}

	private synchronized Class<?> searchFelixBundleClasses(final String className){
		Class<?> clazz = null;

		// try to find the class in the Felix bundles
		CFMLEngine engine = getCFMLEngine();

		if (engine != null) {
			BundleContext bc = engine.getBundleContext();
			if (bc != null) {
				Bundle[] bundles = bc.getBundles();
				Bundle b = null;
				for (int i = 0; i < bundles.length; i++) {
					b = bundles[i];
					//getLogger().debug("ehcache", "Searching " + b.getSymbolicName() + " bundle for " + className + " class.");
					if (b != null && !isFrameworkBundle(b)) {
						try {
							getLogger().debug("ehcache", "Found in " + className + " class in " + b.getSymbolicName() + " bundle.");
							return b.loadClass(className);
						}
						catch (Exception e) {
							clazz = null;
						}
					}
				}
			}
		}

		return clazz;
	}
	
	public ClassLoader getClassLoader() {
		return EHCacheClassLoader.class.getClassLoader();
	}

}
